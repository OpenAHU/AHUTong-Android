import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import ci_release


def gradle(name="3.4.2", code=304003):
    return f'defaultConfig {{\n    versionCode = {code}\n    versionName = "{name}"\n}}\n'


def release(name, code):
    return {"version_name": name, "version_code": code}


class DeployRootTest(unittest.TestCase):
    def test_literal_absolute_directories_are_preserved(self):
        for root in ("/srv/releases/static", "/opt/app-1.2/update_root", "/srv/.updates"):
            with self.subTest(root=root):
                self.assertEqual(root, ci_release.validate_deploy_root(root))

    def test_root_traversal_shell_and_scp_metacharacters_are_rejected(self):
        for root in (None, "", "/", "relative/path", "/srv/releases/", "/srv//releases", "/srv/./releases",
                     "/srv/../releases", "/srv/a b", "/srv/a\nb", "/srv/a\r", "/srv/更新",
                     "/srv/$(id)", "/srv/`id`", "/srv/a;id", "/srv/a'b", '/srv/a"b',
                     "/srv/a:b", "/srv/*", "/srv/a\\b", "/srv/a\t"):
            with self.subTest(root=root), self.assertRaisesRegex(ValueError, "DEPLOY_ROOT"):
                ci_release.validate_deploy_root(root)


class PlanningTest(unittest.TestCase):
    def test_version_table(self):
        cases = [
            ({}, "3.4.2", "major", "4.0.0"),
            ({}, "3.4.2", "minor", "3.5.0"),
            ({}, "3.4.2", "patch", "3.4.3"),
            ({}, "3.4.2", "test", "3.4.3-beta1"),
            ({"stable": release("3.4.2", 304005), "preview": release("3.4.3-release-beta2", 304010)}, "3.4.2", "test", "3.4.3-beta3"),
            ({"stable": release("3.4.2", 304005), "preview": release("3.4.3-beta2", 304010)}, "3.4.2", "test", "3.4.3-beta3"),
            ({"stable": release("3.4.2", 304005), "preview": release("3.4.3-release-beta2", 304010)}, "3.4.2", "patch", "3.4.3"),
            ({"stable": release("3.5.0", 305000)}, "3.4.2", "patch", "3.5.1"),
            ({"stable": release("3.5.0", 305000)}, "3.4.2", "minor", "3.6.0"),
            ({"stable": release("3.4.2", 304000)}, "3.4.3", "patch", "3.4.3"),
            ({"stable": release("3.4.2", 304000)}, "4.0.0", "major", "4.0.0"),
            ({"stable": release("3.4.2", 304000)}, "4.0.0", "patch", "4.0.0"),
            ({"stable": release("3.4.2", 304000), "preview": release("3.4.3-release-beta2", 304006)}, "3.4.3-release-beta4", "test", "3.4.3-beta5"),
            ({"stable": release("3.4.2", 304000), "preview": release("3.4.3-release-beta2", 304006)}, "3.4.3-beta4", "test", "3.4.3-beta5"),
            ({"stable": release("3.4.2", 304000), "preview": release("3.4.3-beta6", 304006)}, "3.4.3-release-beta4", "test", "3.4.3-beta7"),
            ({}, "3.4.3-release-beta2", "patch", "3.4.3"),
            ({}, "3.4.3-release-beta2", "test", "3.4.3-beta3"),
            ({}, "3.4.3-beta2", "patch", "3.4.3"),
            ({}, "3.4.3-beta2", "test", "3.4.3-beta3"),
            ({"stable": None, "preview": None}, "3.4.2", "patch", "3.4.3"),
            ({"stable": release("3.4.2", 304000), "preview": release("3.5.0-release-beta7", 305007)}, "3.4.2", "test", "3.4.3-beta1"),
        ]
        for state, local, change, expected in cases:
            with self.subTest(state=state, local=local, change=change):
                plan = ci_release.plan_release(state, gradle(local), change, "修复问题", False)
                self.assertEqual(expected, plan["version_name"])
                self.assertEqual("preview" if change == "test" else "stable", plan["channel"])
                self.assertEqual(max([304003] + [item["version_code"] for item in state.values() if item]) + 1, plan["version_code"])
                self.assertIs(plan["force"], False)
                self.assertEqual({"version_code", "version_name", "channel", "changelog", "force"}, set(plan))

    def test_current_server_debug_preview_migrates_to_beta(self):
        # Public channel metadata checked on 2026-10-19; keep CI offline.
        state = {"stable": release("3.4.2", 304003), "preview": release("3.4.3-debug2", 304005)}
        for change, expected in (("test", "3.4.3-beta3"), ("patch", "3.4.3")):
            with self.subTest(change=change):
                plan = ci_release.plan_release(state, gradle(), change, "修复问题", False)
                self.assertEqual(expected, plan["version_name"])
                self.assertEqual(304006, plan["version_code"])

    def test_changelog_actual_and_literal_newlines(self):
        for text in ("第一项\n第二项", r"第一项\n第二项", "第一项\r\n第二项"):
            with self.subTest(text=text):
                plan = ci_release.plan_release({}, gradle(), "patch", text, True)
                self.assertEqual("第一项\n第二项", plan["changelog"])
                self.assertIs(plan["force"], True)
        for text in ("", " \n\t ", r"\n\n", None):
            with self.subTest(empty=text), self.assertRaises(ValueError):
                ci_release.plan_release({}, gradle(), "patch", text, False)

    def test_gradle_literals_ignore_comments_suffix_and_property_override(self):
        text = '// versionCode = 999999\n' + gradle() + '\nversionNameSuffix = "-debug"\nversionCode = providers.gradleProperty("release.versionCode").get().toInt()\n'
        self.assertEqual("3.4.3", ci_release.plan_release({}, text, "patch", "ok", False)["version_name"])
        self.assertEqual(304004, ci_release.plan_release({}, text, "patch", "ok", False)["version_code"])

    def test_invalid_codes_and_android_limit(self):
        for code in (0, -1, True, "304003", 2100000001):
            with self.subTest(code=code), self.assertRaises(ValueError):
                ci_release.plan_release({"stable": release("3.4.2", code)}, gradle(), "patch", "ok", False)
        with self.assertRaises(ValueError):
            ci_release.plan_release({}, gradle(code=2100000000), "patch", "ok", False)
        self.assertEqual(2100000000, ci_release.plan_release({}, gradle(code=2099999999), "patch", "ok", False)["version_code"])

    def test_invalid_state_and_versions(self):
        for state in ([], {"stable": {}}, {"stable": 4}, {"stable": release("3.4.3-release-beta1", 304004)}, {"stable": release("3.4.3-beta1", 304004)}, {"preview": release("3.4", 304004)}):
            with self.subTest(state=state), self.assertRaises(ValueError):
                ci_release.plan_release(state, gradle(), "patch", "ok", False)
        for name in ("3.4", "03.4.2", "3.4.2-release-beta0", "3.4.2-beta0", "3.4.2-debug", "3.4.2-debug0"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                ci_release.plan_release({}, gradle(name), "patch", "ok", False)
        with self.assertRaises(ValueError):
            ci_release.plan_release({}, gradle(), "invalid", "ok", False)
        with self.assertRaises(ValueError):
            ci_release.plan_release({}, gradle(), "patch", "ok", "false")
        with self.assertRaises(ValueError):
            ci_release.plan_release({}, "versionCode = code\nversionName = name\n", "patch", "ok", False)


class MetadataTest(unittest.TestCase):
    def test_writes_only_required_artifacts_and_digest(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / "unsigned.apk"
            payload = b"synthetic APK bytes\x00\xff"
            source.write_bytes(payload)
            plan = ci_release.plan_release({}, gradle(), "test", r"第一项\n第二项", True)
            out = root / "metadata"
            result = ci_release.write_metadata(plan, source, out)
            self.assertEqual({"ahutong.apk", "apk_version.txt", "apk_version_name.txt", "apk_changelog.txt", "apk_force.txt", "release.json"}, {p.name for p in out.iterdir()})
            self.assertEqual(payload, (out / "ahutong.apk").read_bytes())
            self.assertEqual("304004\n", (out / "apk_version.txt").read_text())
            self.assertEqual("3.4.3-beta1\n", (out / "apk_version_name.txt").read_text())
            self.assertEqual("第一项\n第二项\n", (out / "apk_changelog.txt").read_text())
            self.assertEqual("true\n", (out / "apk_force.txt").read_text())
            self.assertEqual(hashlib.sha256(payload).hexdigest(), result["sha256"])
            self.assertEqual(len(payload), result["size"])
            self.assertEqual(result, json.loads((out / "release.json").read_text()))
            self.assertEqual(result, ci_release.write_metadata(plan, out / "ahutong.apk", out))

    def test_metadata_rejects_a_legacy_plan_before_creating_output(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / "build.apk"
            apk.write_bytes(b"synthetic APK")
            plan = ci_release.plan_release({}, gradle(), "test", "ok", False)
            for legacy in ("3.4.3-release-beta7", "3.4.3-debug2"):
                with self.subTest(legacy=legacy):
                    plan["version_name"] = legacy
                    with self.assertRaisesRegex(ValueError, "[Rr]eplan.*rebuild"):
                        ci_release.write_metadata(plan, apk, root / "out")
                    self.assertFalse((root / "out").exists())
                    self.assertEqual(legacy, plan["version_name"])

    def test_rejects_empty_apk_and_invalid_plan_before_creating_output(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / "empty.apk"
            apk.touch()
            plan = ci_release.plan_release({}, gradle(), "patch", "ok", False)
            with self.assertRaises(ValueError):
                ci_release.write_metadata(plan, apk, root / "out")
            self.assertFalse((root / "out").exists())
            apk.write_bytes(b"synthetic")
            for change in ({"channel": "preview"}, {"force": "false"}, {"changelog": " "}, {"version_code": True}):
                with self.subTest(change=change), self.assertRaises(ValueError):
                    ci_release.write_metadata(plan | change, apk, root / "out")
                self.assertFalse((root / "out").exists())

    def test_cli_plan_and_write(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "state.json").write_text(json.dumps({"stable": release("3.4.2", 304003), "preview": release("3.4.3-release-beta2", 304005)}))
            (root / "build.gradle.kts").write_text(gradle())
            (root / "changes.txt").write_text(r"修复一\n修复二", encoding="utf-8")
            script = str(Path(ci_release.__file__))
            subprocess.run([sys.executable, script, "plan", "--state", str(root / "state.json"), "--gradle", str(root / "build.gradle.kts"), "--change", "test", "--changelog-file", str(root / "changes.txt"), "--force", "false", "--output", str(root / "plan.json")], check=True, capture_output=True, text=True)
            plan = json.loads((root / "plan.json").read_text())
            self.assertEqual("3.4.3-beta3", plan["version_name"])
            self.assertEqual(304006, plan["version_code"])
            self.assertIs(plan["force"], False)
            (root / "build.apk").write_bytes(b"synthetic APK")
            subprocess.run([sys.executable, script, "write", "--plan", str(root / "plan.json"), "--apk", str(root / "build.apk"), "--output-dir", str(root / "out")], check=True, capture_output=True, text=True)
            self.assertEqual("false\n", (root / "out/apk_force.txt").read_text())


if __name__ == "__main__":
    unittest.main()
