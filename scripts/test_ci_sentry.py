import argparse
from contextlib import ExitStack, redirect_stderr
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
import zipfile

import ci_sentry


UUID = "3e674da3-cbe1-4281-9a49-f1e975012e08"
OTHER_UUID = "be35f9b1-f73d-4897-9d47-f52a11b9c52c"
PIN = "ab" * 32


class SentryFixture:
    """Synthetic APK/JAR bytes; Android tools and all network/CLI calls are mocked."""

    def __init__(self, root):
        self.root = Path(root)
        self.apk = self.root / "signed.apk"
        self.write_apk(UUID)
        self.mapping = self.root / "mapping.txt"
        self.mapping.write_text("com.example.Demo -> a:\n", encoding="utf-8")
        self.uuid_file = self.root / "generated.properties"
        self.uuid_file.write_text(f"io.sentry.ProguardUuids={UUID}\n", encoding="utf-8")
        self.plan = self.root / "plan.json"
        self.write_plan()
        self.tools = self.root / "sdk/build-tools/37.0.0"
        self.tools.mkdir(parents=True)
        for name in ("aapt2", "apksigner"):
            (self.tools / name).write_text("mock tool", encoding="utf-8")
        self.gradle = self.root / "gradle"
        self.jar = (self.gradle / "caches/modules-2/files-2.1/io.sentry/sentry-android-gradle-plugin"
                    / "6.21.0/hash/sentry-android-gradle-plugin-6.21.0.jar")
        self.jar.parent.mkdir(parents=True)
        self.write_jar()
        self.bundle = self.root / "bundle"
        self.badging = "package: name='com.ahu.ahutong' versionCode='304004' versionName='3.4.3'\n"
        self.signature = f"Signer #1 certificate DN: not-to-be-logged\nSigner #1 certificate SHA-256 digest: {PIN}\n"
        self.cli_version = "sentry-cli 3.7.0\n"
        self.env = {
            "ANDROID_SDK_ROOT": str(self.root / "sdk"), "ANDROID_CERT_SHA256": PIN,
            "SENTRY_AUTH_TOKEN": "fixture-token", "SENTRY_ORG": "openahu", "SENTRY_PROJECT": "ahutong-android",
            "GITHUB_SERVER_URL": "https://github.com", "GITHUB_REPOSITORY": "OpenAHU/Ahutong-Android",
            "GITHUB_RUN_ID": "12345",
        }

    def write_plan(self, **overrides):
        self.plan.write_text(json.dumps({"version_code": 304004, "version_name": "3.4.3",
                                        "channel": "stable", **overrides}), encoding="utf-8")

    def write_apk(self, value):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("assets/sentry-debug-meta.properties", f"io.sentry.ProguardUuids={value}\n")

    def write_jar(self, include_cli=True):
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("version.txt", "6.21.0")
            if include_cli:
                archive.writestr("bin/sentry-cli-Linux-x86_64", b"mock executable")

    def args(self):
        return argparse.Namespace(apk=str(self.apk), mapping=str(self.mapping), uuid_file=str(self.uuid_file),
                                  plan=str(self.plan), output_dir=str(self.bundle), gradle_home=str(self.gradle))

    def subprocess(self, command, **kwargs):
        if command[0].endswith("aapt2"):
            output = self.badging
        elif command[0].endswith("apksigner"):
            output = self.signature
        elif command[1:] == ["--version"]:
            output = self.cli_version
        else:
            output = ""
        return subprocess.CompletedProcess(command, 0, stdout=output, stderr="")

    def patches(self):
        stack = ExitStack()
        stack.enter_context(patch.dict(os.environ, self.env, clear=True))
        stack.enter_context(patch.object(ci_sentry, "PLUGIN_SHA256", ci_sentry.sha256(self.jar)))
        stack.enter_context(patch.object(ci_sentry.subprocess, "run", side_effect=self.subprocess))
        return stack

    def prepared(self):
        manifest = ci_sentry.prepare(self.args())
        self.release_info = self.root / "release.json"
        self.release_info.write_text(json.dumps({key: manifest[key] for key in
                                                ("version_code", "version_name", "channel")}
                                               | {"sha256": manifest["apk_sha256"]}), encoding="utf-8")
        return manifest

    def record_args(self):
        return argparse.Namespace(manifest=str(self.bundle / "sentry.json"),
                                  release_info=str(self.release_info), cli=str(self.bundle / "sentry-cli"))


class SignatureTest(unittest.TestCase):
    def test_accept_supported_signer_labels(self):
        labels = ("Signer #1", "Signer:", "V1 Signer:", "V2 Signer:",
                  "V3.0 Signer:", "V3.1 Signer:")
        for label in labels:
            with self.subTest(label=label):
                output = (f"{label} certificate DN: not-to-be-logged\n"
                          f"{label} certificate SHA-256 digest: {PIN}\n")
                with patch.dict(os.environ, ANDROID_CERT_SHA256=":".join(["AB"] * 32)), \
                        patch.object(ci_sentry, "run", return_value=output) as run:
                    ci_sentry.verify_signature("signed.apk", "apksigner")
                run.assert_called_once_with(["apksigner", "verify", "--print-certs", "signed.apk"])

    def test_reject_wrong_certificate_for_each_format(self):
        for label in ("Signer #1", "V3.0 Signer:"):
            with self.subTest(label=label):
                output = f"{label} certificate SHA-256 digest: {'cd' * 32}\n"
                with patch.dict(os.environ, ANDROID_CERT_SHA256=PIN), \
                        patch.object(ci_sentry, "run", return_value=output):
                    with self.assertRaisesRegex(ci_sentry.ValidationError, "does not match"):
                        ci_sentry.verify_signature("signed.apk", "apksigner")

    def test_reject_missing_certificate_without_using_other_digests(self):
        outputs = ("", f"Source Stamp Signer: certificate SHA-256 digest: {PIN}\n",
                   f"Signer #1 public key SHA-256 digest: {PIN}\n",
                   "V3.0 Signer: certificate SHA-256 digest: invalid\n")
        for output in outputs:
            with self.subTest(output=output):
                with patch.dict(os.environ, ANDROID_CERT_SHA256=PIN), \
                        patch.object(ci_sentry, "run", return_value=output):
                    with self.assertRaisesRegex(ci_sentry.ValidationError, "Cannot read APK signing certificate"):
                        ci_sentry.verify_signature("signed.apk", "apksigner")

    def test_reject_multiple_certificate_entries_even_when_they_match(self):
        for other in (PIN, "cd" * 32):
            with self.subTest(other=other):
                output = (f"Signer #1 certificate SHA-256 digest: {PIN}\n"
                          f"Signer #2 certificate SHA-256 digest: {other}\n")
                with patch.dict(os.environ, ANDROID_CERT_SHA256=PIN), \
                        patch.object(ci_sentry, "run", return_value=output):
                    with self.assertRaisesRegex(ci_sentry.ValidationError, "exactly one"):
                        ci_sentry.verify_signature("signed.apk", "apksigner")

    def test_ignore_source_stamp_when_app_certificate_is_present(self):
        output = (f"V3.0 Signer: certificate SHA-256 digest: {PIN}\n"
                  f"Source Stamp Signer: certificate SHA-256 digest: {'cd' * 32}\n")
        with patch.dict(os.environ, ANDROID_CERT_SHA256=PIN), \
                patch.object(ci_sentry, "run", return_value=output):
            ci_sentry.verify_signature("signed.apk", "apksigner")


class ArtifactTest(unittest.TestCase):
    def test_prepare_stable_and_preview(self):
        for channel, name in (("stable", "3.4.3"), ("preview", "3.4.3-beta1")):
            with self.subTest(channel=channel), tempfile.TemporaryDirectory() as root:
                fixture = SentryFixture(root)
                fixture.write_plan(channel=channel, version_name=name)
                fixture.badging = fixture.badging.replace("3.4.3", name)
                fixture.env["ANDROID_CERT_SHA256"] = ":".join(["AB"] * 32)
                with fixture.patches():
                    manifest = ci_sentry.prepare(fixture.args())
                self.assertEqual(f"com.ahu.ahutong@{name}+304004", manifest["release"])
                self.assertEqual(UUID, manifest["uuid"])
                self.assertEqual(hashlib.sha256(b"mock executable").hexdigest(), manifest["cli_sha256"])
                self.assertEqual(fixture.mapping.read_bytes(), (fixture.bundle / "mapping.txt").read_bytes())
                self.assertEqual(0o700, (fixture.bundle / "sentry-cli").stat().st_mode & 0o777)
                self.assertNotIn("not-to-be-logged", (fixture.bundle / "sentry.json").read_text())
                self.assertEqual({"mapping.txt", "uuid.properties", "sentry-cli", "sentry.json"},
                                 {p.name for p in fixture.bundle.iterdir()})

    def test_reject_invalid_artifact_table(self):
        mutations = {
            "uuid": lambda f: f.write_apk(OTHER_UUID),
            "invalid uuid": lambda f: f.write_apk("invalid"),
            "name": lambda f: setattr(f, "badging", f.badging.replace("3.4.3", "3.4.4")),
            "code": lambda f: setattr(f, "badging", f.badging.replace("304004", "304005")),
            "package": lambda f: setattr(f, "badging", f.badging.replace("com.ahu.ahutong", "com.ahutong.preview")),
            "signature": lambda f: os.environ.update(ANDROID_CERT_SHA256="cd" * 32),
            "missing pin": lambda f: os.environ.pop("ANDROID_CERT_SHA256"),
            "missing signer": lambda f: setattr(f, "signature", "Signer #1 certificate DN: not-to-be-logged"),
            "mapping": lambda f: f.mapping.unlink(),
            "missing CLI": lambda f: f.write_jar(False),
            "missing JAR": lambda f: f.jar.unlink(),
            "CLI version": lambda f: setattr(f, "cli_version", "sentry-cli 3.8.0\n"),
        }
        for name, mutate in mutations.items():
            with self.subTest(case=name), tempfile.TemporaryDirectory() as root:
                fixture = SentryFixture(root)
                with fixture.patches():
                    mutate(fixture)
                    # Missing-member and integrity checks are separate failure cases.
                    if name == "missing CLI":
                        with patch.object(ci_sentry, "PLUGIN_SHA256", ci_sentry.sha256(fixture.jar)):
                            with self.assertRaises(ci_sentry.ValidationError):
                                ci_sentry.prepare(fixture.args())
                    else:
                        with self.assertRaises(ci_sentry.ValidationError):
                            ci_sentry.prepare(fixture.args())

    def test_reject_unverified_plugin_and_signature_tool_failure(self):
        for failure in ("JAR checksum", "signer exit"):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as root:
                fixture = SentryFixture(root)
                with fixture.patches():
                    if failure == "JAR checksum":
                        fixture.jar.write_bytes(b"changed artifact")
                        with self.assertRaises(ci_sentry.ValidationError):
                            ci_sentry.prepare(fixture.args())
                    else:
                        def fail_signer(command, **kwargs):
                            if command[0].endswith("apksigner"):
                                raise subprocess.CalledProcessError(1, "apksigner", stderr="private DN")
                            return fixture.subprocess(command, **kwargs)
                        with patch.object(ci_sentry.subprocess, "run", side_effect=fail_signer):
                            captured = io.StringIO()
                            argv = ["prepare", "--apk", str(fixture.apk), "--mapping", str(fixture.mapping),
                                    "--uuid-file", str(fixture.uuid_file), "--plan", str(fixture.plan),
                                    "--output-dir", str(fixture.bundle), "--gradle-home", str(fixture.gradle)]
                            with redirect_stderr(captured):
                                self.assertEqual(1, ci_sentry.main(argv))
                            self.assertNotIn("private DN", captured.getvalue())
                            self.assertNotIn("fixture-token", captured.getvalue())


class ReadbackTest(unittest.TestCase):
    def test_mapping_readback_is_get_with_exact_uuid_and_private_header(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = SentryFixture(root)
            with fixture.patches():
                fixture.prepared()
                with patch.object(ci_sentry, "urlopen", return_value=io.StringIO(json.dumps([
                    {"symbolType": "proguard", "debugId": UUID}])) ) as request:
                    self.assertEqual(0, ci_sentry.main(["verify-mapping", "--manifest", str(fixture.bundle / "sentry.json")]))
                sent = request.call_args.args[0]
                self.assertEqual("GET", sent.get_method())
                self.assertIn(f"debug_id={UUID}", sent.full_url)
                self.assertNotIn("fixture-token", sent.full_url)
                self.assertEqual("Bearer fixture-token", sent.get_header("Authorization"))

    def test_mapping_rejects_timeout_wrong_uuid_and_native_symbols(self):
        for rows in ([], [{"symbolType": "proguard", "uuid": OTHER_UUID}], [{"symbolType": "elf", "debugId": UUID}]):
            with self.subTest(rows=rows), tempfile.TemporaryDirectory() as root:
                fixture = SentryFixture(root)
                with fixture.patches():
                    fixture.prepared()
                    with patch.object(ci_sentry, "get_json", return_value=rows) as request, patch.object(ci_sentry.time, "sleep"):
                        with self.assertRaises(ci_sentry.ValidationError):
                            ci_sentry.verify_mapping(argparse.Namespace(manifest=str(fixture.bundle / "sentry.json")))
                    self.assertEqual(ci_sentry.MAPPING_ATTEMPTS, request.call_count)

    def test_mapping_auth_failure_is_not_ignored_or_retried(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = SentryFixture(root)
            with fixture.patches():
                fixture.prepared()
                error = HTTPError("https://sentry.io/", 403, "Forbidden", {}, None)
                with patch.object(ci_sentry, "get_json", side_effect=error) as request:
                    with self.assertRaises(ci_sentry.ValidationError):
                        ci_sentry.verify_mapping(argparse.Namespace(manifest=str(fixture.bundle / "sentry.json")))
                self.assertEqual(1, request.call_count)


class RecordingTest(unittest.TestCase):
    def test_record_finalizes_and_creates_only_missing_deployment(self):
        for exists in (False, True):
            with self.subTest(exists=exists), tempfile.TemporaryDirectory() as root:
                fixture = SentryFixture(root)
                with fixture.patches():
                    manifest = fixture.prepared()
                    (fixture.bundle / "sentry-cli").chmod(0o644)
                    deployments = [{"environment": "release", "name": "stable-304004"}] if exists else []
                    with patch.object(ci_sentry, "get_json", return_value=deployments), patch.object(ci_sentry, "run") as command:
                        ci_sentry.record(fixture.record_args())
                    self.assertEqual(1 if exists else 2, command.call_count)
                    self.assertEqual(0o700, (fixture.bundle / "sentry-cli").stat().st_mode & 0o777)
                    self.assertEqual(["releases", "finalize", manifest["release"]], command.call_args_list[0].args[0][1:])
                    if not exists:
                        args = command.call_args_list[1].args[0]
                        self.assertEqual("https://github.com/OpenAHU/Ahutong-Android/actions/runs/12345", args[-1])
                        self.assertNotIn("fixture-token", args)

    def test_record_rejects_mismatch_before_any_write(self):
        for problem in ("CLI", "release version", "APK checksum", "GitHub identity"):
            with self.subTest(problem=problem), tempfile.TemporaryDirectory() as root:
                fixture = SentryFixture(root)
                with fixture.patches():
                    fixture.prepared()
                    if problem == "CLI":
                        (fixture.bundle / "sentry-cli").write_bytes(b"changed")
                    elif problem == "GitHub identity":
                        os.environ.pop("GITHUB_RUN_ID")
                    else:
                        info = json.loads(fixture.release_info.read_text())
                        info["version_code" if problem == "release version" else "sha256"] = "changed"
                        fixture.release_info.write_text(json.dumps(info))
                    with patch.object(ci_sentry, "run") as command, patch.object(ci_sentry, "get_json") as request:
                        with self.assertRaises(ci_sentry.ValidationError):
                            ci_sentry.record(fixture.record_args())
                    command.assert_not_called()
                    request.assert_not_called()

    def test_record_stops_on_finalize_or_lookup_failure(self):
        for fail in ("finalize", "lookup"):
            with self.subTest(fail=fail), tempfile.TemporaryDirectory() as root:
                fixture = SentryFixture(root)
                with fixture.patches():
                    fixture.prepared()
                    with patch.object(ci_sentry, "run", side_effect=ci_sentry.ValidationError("CLI failed") if fail == "finalize" else None) as command:
                        with patch.object(ci_sentry, "get_json", side_effect=HTTPError("https://sentry.io/", 403, "Forbidden", {}, None)):
                            with self.assertRaises(ci_sentry.ValidationError):
                                ci_sentry.record(fixture.record_args())
                    self.assertEqual(1, command.call_count)


if __name__ == "__main__":
    unittest.main()
