"""Exercise the plugin's boxed TextUnit ABI against real Compose classes after R8.

Requires local ui-unit-android AAR, matching Kotlin stdlib, and the SDK's d8.jar.
No Android device or Gradle dependency resolution is needed.
"""

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from zipfile import ZipFile


def run(command, expected_success=True):
    result = subprocess.run([str(item) for item in command], capture_output=True, text=True)
    if expected_success and result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ui-unit-aar", required=True, type=Path)
    parser.add_argument("--kotlin-stdlib", required=True, type=Path)
    parser.add_argument("--r8-jar", required=True, type=Path)
    parser.add_argument("--java-home", type=Path)
    args = parser.parse_args()
    java = Path(shutil.which("java") or "java")
    java_home = args.java_home
    if java_home is None:
        settings = run([java, "-XshowSettings:properties", "-version"])
        java_home = Path(re.search(r"java.home = (.+)", settings.stderr).group(1).strip())
    java = java_home / "bin" / "java"
    javac = java_home / "bin" / "javac"
    rules_file = Path(__file__).resolve().parents[1] / "app" / "proguard-rules.pro"
    # Only these production rules affect the real ui-unit classes in this isolated input.
    rules = [line for line in rules_file.read_text(encoding="utf-8").splitlines()
             if line.startswith("-keep class androidx.compose.ui.unit.")]
    baseline_rules = [line for line in rules
                      if not re.match(r"-keep class androidx\.compose\.ui\.unit\.TextUnit(?:\$Companion)? \{", line)]

    with tempfile.TemporaryDirectory(prefix="ahutong-plugin-text-unit-") as temporary:
        work = Path(temporary)
        unit_jar = work / "ui-unit.jar"
        with ZipFile(args.ui_unit_aar) as archive:
            unit_jar.write_bytes(archive.read("classes.jar"))
        source = work / "PluginTextUnitAbiProbe.java"
        source.write_text("""
public final class PluginTextUnitAbiProbe {
    public static void main(String[] args) throws Exception {
        long packed = androidx.compose.ui.unit.TextUnitKt.getSp(14);
        // The class name comes from the separately loaded plugin, outside R8's input.
        Class<?> type = Class.forName(args[0]);
        Object boxed = type.getDeclaredMethod("box-impl", long.class).invoke(null, packed);
        long unpacked = (Long) type.getDeclaredMethod("unbox-impl").invoke(boxed);
        if (packed != unpacked) throw new AssertionError("TextUnit round trip failed");
        Object companion = type.getDeclaredField("Companion").get(null);
        companion.getClass().getDeclaredMethod("getUnspecified-XSAIIZE").invoke(companion);
        System.out.println("TextUnit plugin ABI OK");
    }
}
""", encoding="utf-8")
        classes = work / "probe"
        classes.mkdir()
        run([javac, "-cp", unit_jar, "-d", classes, source])
        for label, keep_rules in (("baseline", baseline_rules), ("fixed", rules)):
            configuration = work / f"{label}.pro"
            configuration.write_text("\n".join([
                "-keep class PluginTextUnitAbiProbe { *; }",
                "-dontwarn **", "-dontnote **", "-repackageclasses", *keep_rules
            ]), encoding="utf-8")
            output = work / f"{label}.jar"
            run([java, "-cp", args.r8_jar, "com.android.tools.r8.R8", "--classfile",
                 "--release", "--lib", java_home, "--lib", args.kotlin_stdlib,
                 "--pg-conf", configuration, "--output", output,
                 unit_jar, classes / "PluginTextUnitAbiProbe.class"])
            verdict = run([java, "-cp", str(output) + os.pathsep
                           + str(args.kotlin_stdlib), "PluginTextUnitAbiProbe",
                           "androidx.compose.ui.unit.TextUnit"], expected_success=False)
            if label == "baseline":
                if verdict.returncode == 0 or "ClassNotFoundException: androidx.compose.ui.unit.TextUnit" not in verdict.stderr:
                    raise AssertionError("Baseline did not reproduce the missing TextUnit class:\n" + verdict.stderr)
                print("baseline: reproduced ClassNotFoundException: androidx.compose.ui.unit.TextUnit")
            else:
                if verdict.returncode:
                    raise AssertionError("Current production rules still break the plugin ABI:\n" + verdict.stderr)
                print("fixed: " + verdict.stdout.strip())


if __name__ == "__main__":
    main()
