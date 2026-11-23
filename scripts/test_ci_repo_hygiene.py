import pathlib
import tempfile
import unittest

import check_repo_hygiene as hygiene


class RepoHygieneTest(unittest.TestCase):
    def make_minimal_repo(self, root: pathlib.Path) -> list[str]:
        (root / "AGENTS.md").write_text("# Rules\n", encoding="utf-8")
        (root / "settings.gradle.kts").write_text('include(":app")\ninclude(":core:plugin-platform")\n', encoding="utf-8")
        (root / "settings-gradle.lockfile").write_text("", encoding="utf-8")
        (root / "app").mkdir()
        (root / "app" / "gradle.lockfile").write_text("", encoding="utf-8")
        (root / "app" / "proguard-rules.pro").write_text(
            "-keep class com.ahu.ahutong.data.model.** { *; }\n", encoding="utf-8"
        )
        (root / "gradle").mkdir()
        (root / "gradle" / "libs.versions.toml").write_text(
            '[versions]\ncomposeBom = "2025.10.01"\nkotlin = "2.2.21"\ncoroutines = "1.10.2"\ncoilVersion = "2.7.0"\n',
            encoding="utf-8",
        )
        (root / "core" / "plugin-platform").mkdir(parents=True)
        (root / "core" / "plugin-platform" / "build.gradle.kts").write_text(
            'api(platform("androidx.compose:compose-bom:2025.10.01"))\n'
            'api("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")\n'
            'api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")\n'
            'api("io.coil-kt:coil-compose:2.7.0")\n',
            encoding="utf-8",
        )
        skill = root / ".agents" / "skills" / "demo-skill"
        skill.mkdir(parents=True)
        (skill / "SKILL.md").write_text(
            "---\nname: demo-skill\ndescription: demo\n---\n# Demo\n", encoding="utf-8"
        )
        return ["AGENTS.md", "settings.gradle.kts", "app/proguard-rules.pro"]

    def test_valid_minimal_repo_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            paths = self.make_minimal_repo(root)
            self.assertEqual([], hygiene.check_repo(root, paths))

    def test_rejects_personal_path_and_bugly_literal_and_duplicate_proguard(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            paths = self.make_minimal_repo(root)
            (root / "scripts").mkdir()
            bad_py = root / "scripts" / "bad.py"
            bad_py.write_text(
                'SDK = r"' + "\\".join(["C:", "Us" + "ers", "Alice", "Sdk"]) + '"\n',
                encoding="utf-8",
            )
            bad_sh = root / "scripts" / "bad.sh"
            bad_sh.write_text(
                'SDK="' + "/".join(["", "ho" + "me", "alice", "sdk"]) + '"\n',
                encoding="utf-8",
            )
            bad_kts = root / "scripts" / "bad.kts"
            bad_kts.write_text(
                'val sdk = "' + "/".join(["", "Us" + "ers", "alice", "sdk"]) + '"\n',
                encoding="utf-8",
            )
            (root / "app" / "Bad.kt").write_text('const val ID = "2c2ccadcad"\n', encoding="utf-8")
            (root / "app" / "proguard-rules.pro").write_text(
                "-keep class A { *; }\n-keep class A { *; }\n", encoding="utf-8"
            )
            errors = hygiene.check_repo(
                root,
                paths + ["scripts/bad.py", "scripts/bad.sh", "scripts/bad.kts", "app/Bad.kt"],
            )
            self.assertEqual(5, len(errors))

    def test_current_repo_passes_hygiene(self):
        repo_root = pathlib.Path(__file__).resolve().parent.parent
        self.assertEqual([], hygiene.check_repo(repo_root))
        self.assertEqual(
            [],
            hygiene.check_repo(
                repo_root,
                ["scripts/check_repo_hygiene.py", "scripts/test_ci_repo_hygiene.py"],
            ),
        )

    def test_rejects_missing_skill_md_and_bom_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            paths = self.make_minimal_repo(root)
            (root / ".agents" / "skills" / "broken-skill").mkdir(parents=True)
            (root / "core" / "plugin-platform" / "build.gradle.kts").write_text(
                'api(platform("androidx.compose:compose-bom:2024.01.00"))\n', encoding="utf-8"
            )
            errors = hygiene.check_repo(root, paths)
            self.assertTrue(any("broken-skill" in e for e in errors))
            self.assertTrue(any("compose-bom:2025.10.01" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
