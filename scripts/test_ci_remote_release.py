#!/usr/bin/env python3
"""Local filesystem fixtures only; never contacts a publication server."""

import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import tempfile
import threading
import unittest
from unittest.mock import patch
import zipfile

import ci_remote_release as release


class RemoteReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.root = self.base / "static"
        self.root.mkdir()
        self.counter = 0

    def seed(self, channel="stable", code=10):
        target = self.root if channel == "stable" else self.root / "preview"
        target.mkdir(exist_ok=True)
        files = {
            release.APK: f"old-{channel}-apk".encode(),
            release.VERSION: f"{code}\n".encode(),
            release.VERSION_NAME: f"old-{channel}\n".encode(),
            release.CHANGELOG: b"old notes\n",
            release.FORCE: b"false\n",
            release.RELEASE: b'{"old":true}\n',
        }
        for name, data in files.items():
            (target / name).write_bytes(data)
        return files

    def contents(self, channel="stable"):
        target = self.root if channel == "stable" else self.root / "preview"
        return {name: (target / name).read_bytes() if (target / name).exists() else None
                for name in release.DEPLOY_FILES}

    def payload(self, channel="stable", code=11, expected=None):
        apk = b"new-fixture-apk"
        plan = {
            "channel": channel, "version_code": code, "version_name": "3.4.3",
            "sha256": hashlib.sha256(apk).hexdigest(), "size": len(apk),
            "force": False, "changelog": "new notes",
        }
        return {
            release.APK: apk,
            release.VERSION: f"{code}\n".encode(),
            release.VERSION_NAME: b"3.4.3\n",
            release.CHANGELOG: b"new notes\n",
            release.FORCE: b"false\n",
            release.RELEASE: json.dumps(plan).encode(),
            release.EXPECTED: json.dumps(expected or release.state(self.root)).encode(),
        }

    def bundle(self, payload=None, special=None):
        self.counter += 1
        path = self.base / f"bundle-{self.counter}.zip"
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in (payload or self.payload()).items():
                archive.writestr(name, data)
            if special:
                archive.writestr(*special)
        return path

    def test_empty_state_and_fingerprint_cover_each_channel_file(self):
        empty = release.state(self.root)
        self.assertIsNone(empty["stable"])
        self.assertIsNone(empty["preview"])
        self.assertEqual(64, len(empty["fingerprint"]))
        for channel in ("stable", "preview"):
            files = self.seed(channel)
            for name, data in files.items():
                original = release.state(self.root)["fingerprint"]
                path = (self.root if channel == "stable" else self.root / "preview") / name
                path.write_bytes(b"11\n" if name == release.VERSION else data + b"x")
                self.assertNotEqual(original, release.state(self.root)["fingerprint"], (channel, name))
                path.write_bytes(data)
                path.unlink()
                self.assertNotEqual(original, release.state(self.root)["fingerprint"], (channel, name, "missing"))
                path.write_bytes(data)

    def test_state_command_emits_json_and_ignores_other_server_files(self):
        self.seed()
        before = release.state(self.root)
        (self.root / "libahutong.so").write_bytes(b"native")
        (self.root / "calendars").mkdir()
        (self.root / "ahutong.apk.bak").write_bytes(b"legacy-backup")
        self.assertEqual(before, release.state(self.root))
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, release.main(["state", "--root", str(self.root)]))
        self.assertEqual(before, json.loads(output.getvalue()))

    def test_publish_each_channel_isolates_other_channel_and_preserves_backup(self):
        for channel, other in (("stable", "preview"), ("preview", "stable")):
            with self.subTest(channel=channel):
                self.seed("stable", 10)
                self.seed("preview", 20)
                old = self.contents(channel)
                other_files = self.contents(other)
                data = self.payload(channel, 21)
                result = release.publish(self.root, self.bundle(data))
                self.assertEqual(channel, result["channel"])
                self.assertEqual(21, release.state(self.root)[channel]["version_code"])
                self.assertEqual(other_files, self.contents(other))
                for name in release.DEPLOY_FILES:
                    self.assertEqual(data[name], self.contents(channel)[name])
                    self.assertEqual(old[name], (Path(result["backup"]) / name).read_bytes())
                self.assertFalse(((self.root if channel == "stable" else self.root / "preview") / release.EXPECTED).exists())

    def test_version_code_exceeds_both_channels(self):
        self.seed("stable", 10)
        self.seed("preview", 20)
        for channel in ("stable", "preview"):
            for code in (10, 20):
                with self.subTest(channel=channel, code=code):
                    with self.assertRaisesRegex(release.ReleaseError, "both existing channels"):
                        release.publish(self.root, self.bundle(self.payload(channel, code)))
        self.assertEqual(10, release.state(self.root)["stable"]["version_code"])

    def test_bundle_rejects_version_code_above_android_limit(self):
        bundle = self.bundle(self.payload(code=2100000001))
        with self.assertRaisesRegex(release.ReleaseError, "2100000000"):
            release.publish(self.root, bundle)
        self.assertIsNone(release.state(self.root)["stable"])

    def test_state_rejects_version_code_above_android_limit(self):
        self.seed(code=2100000001)
        with self.assertRaisesRegex(release.ReleaseError, "version code"):
            release.state(self.root)

    def test_maximum_android_version_code_is_allowed(self):
        bundle = self.bundle(self.payload(code=2100000000))
        release.publish(self.root, bundle)
        self.assertEqual(2100000000, release.state(self.root)["stable"]["version_code"])

    def test_private_umask_produces_public_files_and_private_backup(self):
        previous_umask = os.umask(0o077)
        try:
            self.seed()
            result = release.publish(self.root, self.bundle())
        finally:
            os.umask(previous_umask)
        for name in release.DEPLOY_FILES:
            self.assertEqual(0o644, stat.S_IMODE((self.root / name).stat().st_mode), name)
        backup = Path(result["backup"])
        for directory in (self.root / ".ci-backups", backup.parent, backup):
            self.assertEqual(0o700, stat.S_IMODE(directory.stat().st_mode), directory.name)

    def test_state_changes_in_other_channel_reject_stale_bundle(self):
        self.seed("stable", 10)
        self.seed("preview", 20)
        bundle = self.bundle(self.payload("stable", 21))
        (self.root / "preview" / release.CHANGELOG).write_text("published elsewhere")
        before = self.contents()
        with self.assertRaisesRegex(release.ReleaseError, "state changed"):
            release.publish(self.root, bundle)
        self.assertEqual(before, self.contents())
        self.assertFalse((self.root / ".ci-backups").exists())

    def test_tampered_apk_and_inconsistent_metadata_are_rejected(self):
        self.seed()
        for field, value, message in (
            (release.APK, b"NEW-fixture-apk", "SHA-256"),
            (release.VERSION, b"12", "version metadata"),
            (release.VERSION_NAME, b"wrong", "version metadata"),
            (release.FORCE, b"true", "force metadata"),
            (release.CHANGELOG, b"wrong notes", "changelog metadata"),
        ):
            with self.subTest(field=field):
                data = self.payload()
                data[field] = value
                before = self.contents()
                with self.assertRaisesRegex(release.ReleaseError, message):
                    release.publish(self.root, self.bundle(data))
                self.assertEqual(before, self.contents())

    def test_invalid_plan_types_and_missing_fingerprint_are_rejected(self):
        self.seed()
        for key, value in (("version_code", True), ("size", True), ("channel", "../stable"),
                           ("version_name", "a\nb"), ("force", "false"), ("sha256", "0" * 64)):
            with self.subTest(key=key):
                data = self.payload()
                plan = json.loads(data[release.RELEASE])
                plan[key] = value
                data[release.RELEASE] = json.dumps(plan).encode()
                with self.assertRaises(release.ReleaseError):
                    release.publish(self.root, self.bundle(data))
        data = self.payload()
        data[release.EXPECTED] = b"{}"
        with self.assertRaisesRegex(release.ReleaseError, "fingerprint"):
            release.publish(self.root, self.bundle(data))

    def test_traversal_duplicate_directory_extra_and_symlink_zip_entries_are_rejected(self):
        self.seed()
        for name in ("../escape", "/absolute", "preview/ahutong.apk", "extra", "directory/"):
            with self.subTest(name=name):
                with self.assertRaises(release.ReleaseError):
                    release.publish(self.root, self.bundle(special=(name, b"bad")))
        self.assertFalse((self.base / "escape").exists())
        data = self.payload()
        del data[release.APK]
        symlink = zipfile.ZipInfo(release.APK)
        symlink.create_system = 3
        symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaisesRegex(release.ReleaseError, "Unsupported ZIP entry"):
            release.publish(self.root, self.bundle(data, special=(symlink, b"/etc/passwd")))
        with self.assertWarns(UserWarning):
            duplicate = self.bundle(special=(release.APK, b"duplicate"))
        with self.assertRaises(release.ReleaseError):
            release.publish(self.root, duplicate)

    def test_oversized_zip_payload_is_rejected_before_deployment(self):
        self.seed()
        bundle = self.bundle()
        before = self.contents()
        with patch.object(release, "MAX_PAYLOAD", 10):
            with self.assertRaisesRegex(release.ReleaseError, "too large|exceeds"):
                release.publish(self.root, bundle)
        self.assertEqual(before, self.contents())

    def test_server_symlink_targets_are_rejected(self):
        outside = self.base / "outside"
        outside.mkdir()
        (self.root / "preview").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(release.ReleaseError, "symlink"):
            release.state(self.root)

    def test_mid_commit_failure_restores_existing_and_originally_missing_files(self):
        self.seed()
        (self.root / release.CHANGELOG).unlink()
        before = self.contents()
        bundle = self.bundle()
        replace = os.replace
        calls = []

        def fail_fourth_commit(source, target):
            if Path(source).name in release.DEPLOY_FILES:
                calls.append(Path(target).name)
                if len(calls) == 4:
                    raise OSError("injected write failure")
            return replace(source, target)

        with patch.object(release.os, "replace", side_effect=fail_fourth_commit):
            with self.assertRaisesRegex(release.ReleaseError, "original files were restored"):
                release.publish(self.root, bundle)
        self.assertEqual(before, self.contents())
        backups = list((self.root / ".ci-backups" / "stable").iterdir())
        self.assertEqual(1, len(backups))
        self.assertFalse(json.loads((backups[0] / "backup.json").read_text())["original_files"][release.CHANGELOG])
        self.assertEqual(before[release.APK], (backups[0] / release.APK).read_bytes())
        self.assertEqual([], list(self.root.glob(".ci-stage-*")))

    def test_failed_first_publication_removes_all_new_files(self):
        bundle = self.bundle(self.payload("preview", 1))
        before = release.state(self.root)
        replace = os.replace

        def fail_version(source, target):
            if Path(source).name == release.VERSION:
                raise OSError("injected final commit failure")
            return replace(source, target)

        with patch.object(release.os, "replace", side_effect=fail_version):
            with self.assertRaisesRegex(release.ReleaseError, "original files were restored"):
                release.publish(self.root, bundle)
        self.assertEqual(before, release.state(self.root))
        self.assertTrue(all(value is None for value in self.contents("preview").values()))
        backup = next((self.root / ".ci-backups" / "preview").iterdir())
        manifest = json.loads((backup / "backup.json").read_text())
        self.assertTrue(all(not exists for exists in manifest["original_files"].values()))

    def test_version_is_committed_last(self):
        self.seed()
        bundle = self.bundle()
        replace = os.replace
        calls = []

        def record(source, target):
            calls.append(Path(target).name)
            return replace(source, target)

        with patch.object(release.os, "replace", side_effect=record):
            release.publish(self.root, bundle)
        self.assertEqual(list(release.DEPLOY_FILES), calls)
        self.assertEqual(release.VERSION, calls[-1])

    def test_state_and_publish_wait_for_same_global_lock(self):
        self.seed()
        bundle = self.bundle()
        for operation in (lambda: release.state(self.root), lambda: release.publish(self.root, bundle)):
            attempted = threading.Event()
            completed = threading.Event()
            errors = []
            flock = release.fcntl.flock

            def observe_lock(descriptor, flag):
                attempted.set()
                return flock(descriptor, flag)

            def invoke():
                try:
                    operation()
                except BaseException as error:
                    errors.append(error)
                finally:
                    completed.set()

            with release._locked(self.root):
                with patch.object(release.fcntl, "flock", side_effect=observe_lock):
                    worker = threading.Thread(target=invoke, daemon=True)
                    worker.start()
                    self.assertTrue(attempted.wait(2))
                    self.assertFalse(completed.is_set())
            worker.join(2)
            self.assertTrue(completed.is_set())
            self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
