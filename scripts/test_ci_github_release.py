import copy
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import ci_github_release
import ci_release


COMMIT = "a" * 40
OTHER_COMMIT = "b" * 40
REPOSITORY = "OpenAHU/AHUTong-Android"


class FakeGh:
    """In-memory GitHub; all subprocess calls are intercepted, including binary downloads."""

    def __init__(self):
        self.calls = []
        self.tag = None
        self.tag_objects = {}
        self.release = None
        self.assets = {}
        self.contents = {}
        self.error = None
        self.fail_upload = None
        self.change_tag_after_upload = False
        self.asset_reads = 0

    def add_asset(self, name, content, digest=True):
        asset_id = len(self.assets) + 10
        self.assets[name] = {"id": asset_id, "name": name, "size": len(content), "state": "uploaded",
                             "digest": f"sha256:{hashlib.sha256(content).hexdigest()}" if digest else None}
        self.contents[asset_id] = content

    def run(self, command, **kwargs):
        self.calls.append((command, kwargs.get("input")))
        if command[:3] == ["gh", "release", "upload"]:
            path = Path(command[4])
            if path.name == self.fail_upload:
                return subprocess.CompletedProcess(command, 1, "", "mock upload failure")
            if path.name in self.assets:
                raise AssertionError("Existing asset must never be clobbered")
            self.add_asset(path.name, path.read_bytes())
            return subprocess.CompletedProcess(command, 0, "", "")
        if command[:2] != ["gh", "api"]:
            raise AssertionError(f"Unexpected executable: {command[0]}")
        endpoint = command[2].removeprefix(f"repos/{REPOSITORY}/")
        method = command[command.index("--method") + 1] if "--method" in command else "GET"
        if self.error and (method, endpoint) == self.error[:2]:
            return subprocess.CompletedProcess(command, 1, "", self.error[2])
        body = json.loads(kwargs["input"]) if kwargs.get("input") else None
        if endpoint.startswith("git/ref/tags/"):
            if self.tag is None:
                return subprocess.CompletedProcess(command, 1, "", "gh: Not Found (HTTP 404)")
            value = {"ref": f"refs/tags/{endpoint.split('/')[-1]}", "object": self.tag}
        elif endpoint.startswith("git/tags/"):
            value = {"object": self.tag_objects[endpoint.split("/")[-1]]}
        elif endpoint == "git/refs" and method == "POST":
            self.tag = {"type": "commit", "sha": body["sha"]}
            value = {"ref": body["ref"], "object": self.tag}
        elif endpoint.startswith("releases?"):
            value = [self.release] if self.release else []
        elif endpoint == "releases" and method == "POST":
            self.release = dict(body, id=7)
            value = self.release
        elif endpoint == "releases/7" and method == "PATCH":
            self.release.update(body)
            value = self.release
        elif endpoint.startswith("releases/7/assets?"):
            self.asset_reads += 1
            if self.change_tag_after_upload and self.asset_reads > 1:
                self.tag = {"type": "commit", "sha": OTHER_COMMIT}
            value = list(self.assets.values())
        elif endpoint.startswith("releases/assets/"):
            kwargs["stdout"].write(self.contents[int(endpoint.split("/")[-1])])
            return subprocess.CompletedProcess(command, 0, None, b"")
        else:
            raise AssertionError(f"Unexpected API call: {method} {endpoint}")
        return subprocess.CompletedProcess(command, 0, json.dumps(value), "")

    def mutations(self):
        return [(command, body) for command, body in self.calls
                if command[:3] == ["gh", "release", "upload"] or
                ("--method" in command and command[command.index("--method") + 1] != "GET")]


class GithubReleaseTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.apk = self.root / "signed-fixture.apk"
        self.apk.write_bytes(b"synthetic already-verified signed APK fixture")
        self.plan = {"version_code": 304015, "version_name": "3.4.9-beta1", "channel": "preview",
                     "changelog": "修复扫码\n优化触感", "force": False}
        self.payload = self.root / "payload"
        self.metadata = ci_release.write_metadata(self.plan, self.apk, self.payload)
        self.gh = FakeGh()
        self.mock = patch.object(ci_github_release.subprocess, "run", side_effect=lambda command, **kwargs: self.gh.run(command, **kwargs))
        self.mock.start()
        self.addCleanup(self.mock.stop)

    def publish(self, commit=COMMIT, repository=REPOSITORY):
        return ci_github_release.publish_release(self.payload, repository, commit)

    def rewrite_metadata(self, **changes):
        self.metadata.update(changes)
        (self.payload / "release.json").write_text(json.dumps(self.metadata), encoding="utf-8")

    def existing(self, draft=False, digest=True):
        metadata, changelog, files = ci_github_release.load_payload(self.payload)
        self.gh.tag = {"type": "commit", "sha": COMMIT}
        self.gh.release = {"id": 7, "tag_name": f"v{metadata['version_name']}", "target_commitish": COMMIT,
                           "name": f"AHUTong {metadata['version_name']}", "body": changelog,
                           "draft": draft, "prerelease": metadata["channel"] == "preview"}
        for name, path in files.items():
            self.gh.add_asset(name, path.read_bytes(), digest=digest)

    def test_preview_is_uploaded_as_verified_draft_then_published_without_latest(self):
        # These private/internal payload files must not become public assets.
        for name in ("expected-state.json", "mapping.txt", "sentry-cli", "private.env"):
            (self.payload / name).write_text("private fixture", encoding="utf-8")
        result = self.publish()
        self.assertEqual("v3.4.9-beta1", result["tag"])
        self.assertFalse(result["reused"])
        self.assertEqual({"ahutong.apk", "ahutong.apk.sha256"}, set(self.gh.assets))
        self.assertEqual({"type": "commit", "sha": COMMIT}, self.gh.tag)
        self.assertEqual(COMMIT, self.gh.release["target_commitish"])
        self.assertEqual(self.plan["changelog"], self.gh.release["body"])
        self.assertTrue(self.gh.release["prerelease"])
        self.assertFalse(self.gh.release["draft"])
        self.assertEqual("false", self.gh.release["make_latest"])
        self.assertEqual(f"{self.metadata['sha256']}  ahutong.apk\n",
                         (self.payload / "ahutong.apk.sha256").read_text(encoding="ascii"))
        changes = self.gh.mutations()
        draft = json.loads(changes[1][1])
        self.assertTrue(draft["draft"])
        self.assertEqual("false", draft["make_latest"])
        self.assertEqual("PATCH", changes[-1][0][changes[-1][0].index("--method") + 1])
        self.assertNotIn("--clobber", [argument for command, _ in self.gh.calls for argument in command])

    def test_stable_becomes_latest(self):
        self.plan.update(version_name="3.4.9", channel="stable")
        ci_release.write_metadata(self.plan, self.apk, self.payload)
        result = self.publish()
        self.assertEqual("v3.4.9", result["tag"])
        self.assertFalse(result["prerelease"])
        self.assertEqual("true", self.gh.release["make_latest"])

    def test_identical_published_release_is_reused_without_mutation(self):
        self.existing()
        self.assertTrue(self.publish()["reused"])
        self.assertEqual([], self.gh.mutations())

    def test_partial_draft_resumes_only_missing_asset(self):
        self.existing(draft=True)
        del self.gh.assets["ahutong.apk.sha256"]
        self.publish()
        uploaded = [Path(command[4]).name for command, _ in self.gh.calls if command[:3] == ["gh", "release", "upload"]]
        self.assertEqual(["ahutong.apk.sha256"], uploaded)
        self.assertFalse(self.gh.release["draft"])

    def test_upload_failure_leaves_draft_and_retry_preserves_uploaded_apk(self):
        self.gh.fail_upload = "ahutong.apk.sha256"
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "rerun to resume"):
            self.publish()
        self.assertTrue(self.gh.release["draft"])
        self.assertEqual({"ahutong.apk"}, set(self.gh.assets))
        self.gh.calls.clear()
        self.gh.fail_upload = None
        self.publish()
        uploads = [Path(command[4]).name for command, _ in self.gh.calls if command[:3] == ["gh", "release", "upload"]]
        self.assertEqual(["ahutong.apk.sha256"], uploads)

    def test_tag_conflict_fails_without_modifying_release(self):
        self.existing()
        self.gh.tag["sha"] = OTHER_COMMIT
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "different build commit"):
            self.publish()
        self.assertEqual([], self.gh.mutations())

    def test_annotated_tag_is_peeled_and_must_match_build(self):
        self.existing()
        self.gh.tag = {"type": "tag", "sha": "c" * 40}
        self.gh.tag_objects["c" * 40] = {"type": "commit", "sha": COMMIT}
        self.assertTrue(self.publish()["reused"])
        self.gh.tag_objects["c" * 40]["sha"] = OTHER_COMMIT
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "different build commit"):
            self.publish()

    def test_tag_moving_during_upload_does_not_publish_draft(self):
        self.gh.change_tag_after_upload = True
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "changed during upload"):
            self.publish()
        self.assertTrue(self.gh.release["draft"])

    def test_same_sized_remote_binary_with_different_digest_is_never_replaced(self):
        self.existing(draft=True)
        self.gh.assets["ahutong.apk"]["digest"] = "sha256:" + "f" * 64
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "different sha256"):
            self.publish()
        self.assertEqual([], self.gh.mutations())

    def test_published_missing_asset_is_not_modified(self):
        self.existing()
        del self.gh.assets["ahutong.apk.sha256"]
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "Published release is incomplete"):
            self.publish()
        self.assertEqual([], self.gh.mutations())

    def test_unexpected_remote_asset_blocks_draft_publication(self):
        self.existing(draft=True)
        self.gh.add_asset("mapping.txt", b"private mapping fixture")
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "unexpected or duplicate"):
            self.publish()
        self.assertEqual([], self.gh.mutations())

    def test_old_assets_without_digest_are_downloaded_and_hashed(self):
        self.existing(digest=False)
        self.assertTrue(self.publish()["reused"])
        downloads = [command for command, _ in self.gh.calls if command[2].startswith(f"repos/{REPOSITORY}/releases/assets/")]
        self.assertTrue(downloads)
        self.assertEqual([], self.gh.mutations())

    def test_downloaded_old_asset_conflict_is_not_overwritten(self):
        self.existing(digest=False)
        asset = self.gh.assets["ahutong.apk"]
        original = self.gh.contents[asset["id"]]
        self.gh.contents[asset["id"]] = b"x" + original[1:]
        with self.assertRaisesRegex(ci_github_release.ReleaseError, "bytes do not match"):
            self.publish()
        self.assertEqual([], self.gh.mutations())

    def test_release_metadata_or_source_conflicts_are_not_overwritten(self):
        for change in ({"body": "other changelog"}, {"prerelease": False}, {"target_commitish": OTHER_COMMIT}):
            with self.subTest(change=change):
                self.gh = FakeGh()
                self.existing(draft=True)
                self.gh.release.update(change)
                with self.assertRaisesRegex(ci_github_release.ReleaseError, "refusing to overwrite"):
                    self.publish()
                self.assertEqual([], self.gh.mutations())

    def test_only_real_not_found_is_treated_as_missing_and_errors_are_redacted(self):
        self.gh.error = ("GET", "git/ref/tags/v3.4.9-beta1", "gh: MOCK_PRIVATE_TOKEN (HTTP 403)")
        with self.assertRaises(ci_github_release.ReleaseError) as error:
            self.publish()
        self.assertNotIn("MOCK_PRIVATE_TOKEN", str(error.exception))
        self.assertEqual([], self.gh.mutations())

    def test_invalid_commit_and_repository_fail_before_any_gh_call(self):
        for commit in ("master", "a" * 7, "a" * 40 + "\n", "z" * 40):
            with self.subTest(commit=commit), self.assertRaisesRegex(ci_github_release.ReleaseError, "full 40-character"):
                self.publish(commit=commit)
        for repository in ("owner/repo/extra", "https://github.com/owner/repo", "../repo", "owner/repo\n"):
            with self.subTest(repository=repository), self.assertRaisesRegex(ci_github_release.ReleaseError, "OWNER/REPO"):
                self.publish(repository=repository)
        self.assertEqual([], self.gh.calls)

    def test_payload_conflicts_fail_before_any_remote_mutation(self):
        cases = [({"sha256": "0" * 64}, "sha256"), ({"size": self.metadata["size"] + 1}, "size"),
                 ({"version_code": True}, "version_code"), ({"channel": "stable"}, "channel"),
                 ({"version_name": "3.4.9-debug1"}, "canonical"), ({"apk": "../private.apk"}, "filename"),
                 ({"private_state": "fixture"}, "public release metadata")]
        original = copy.deepcopy(self.metadata)
        for changes, message in cases:
            with self.subTest(changes=changes):
                self.metadata = dict(original)
                self.rewrite_metadata(**changes)
                with self.assertRaisesRegex(ValueError, message):
                    self.publish()
                self.assertEqual([], self.gh.calls)

    def test_version_and_changelog_sidecars_must_agree(self):
        for name in ("apk_version.txt", "apk_version_name.txt", "apk_force.txt", "apk_changelog.txt"):
            with self.subTest(name=name):
                path = self.payload / name
                original = path.read_bytes()
                path.write_text("wrong fixture", encoding="utf-8")
                with self.assertRaises(ValueError):
                    self.publish()
                path.write_bytes(original)
        self.assertEqual([], self.gh.calls)

    def test_literal_newlines_in_source_changelog_are_normalized_for_release_body(self):
        self.rewrite_metadata(changelog=r"第一项\n第二项")
        (self.payload / "apk_changelog.txt").write_bytes("第一项\r\n第二项\r\n".encode("utf-8"))
        self.publish()
        self.assertEqual("第一项\n第二项", self.gh.release["body"])

    def test_cli_requires_token_environment_and_outputs_a_compact_public_result(self):
        args = ["--payload-dir", str(self.payload), "--repo", REPOSITORY, "--commit", COMMIT]
        with patch.dict(os.environ, {}, clear=True), patch("sys.stderr", new_callable=io.StringIO):
            with self.assertRaises(SystemExit) as error:
                ci_github_release.main(args)
            self.assertEqual(2, error.exception.code)
        self.assertEqual([], self.gh.calls)
        with patch.dict(os.environ, {"GH_TOKEN": "synthetic token"}, clear=True), patch("sys.stdout", new_callable=io.StringIO) as output:
            ci_github_release.main(args)
        self.assertEqual("v3.4.9-beta1", json.loads(output.getvalue())["tag"])
        self.assertNotIn("synthetic token", output.getvalue())


if __name__ == "__main__":
    unittest.main()
