#!/usr/bin/env python3
"""Plan releases with X.Y.Z-betaN previews and package local metadata; never publish."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

ANDROID_MAX_VERSION_CODE = 2_100_000_000
_VERSION = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(?:(?:release-)?beta|debug)([1-9]\d*))?")


def _version(name):
    match = _VERSION.fullmatch(name) if isinstance(name, str) else None
    if match is None:
        raise ValueError(f"Invalid version name: {name!r}; expected X.Y.Z or X.Y.Z-betaN (legacy X.Y.Z-release-betaN/debugN are accepted for planning)")
    return tuple(map(int, match.group(1, 2, 3))), int(match[4]) if match[4] else None


def _version_code(value):
    if type(value) is not int or not 1 <= value <= ANDROID_MAX_VERSION_CODE:
        raise ValueError(f"version_code must be an integer in 1..{ANDROID_MAX_VERSION_CODE}")
    return value


def _changelog(text):
    if not isinstance(text, str):
        raise ValueError("Changelog must be text")
    text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\\n", "\n").strip()
    if not text:
        raise ValueError("Changelog must not be empty")
    return text


def _gradle_version(text):
    code = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*(?://[^\n]*)?$", text, re.MULTILINE)
    name = re.search(r'^\s*versionName\s*=\s*"([^"\n]+)"\s*(?://[^\n]*)?$', text, re.MULTILINE)
    if code is None or name is None:
        raise ValueError("Gradle must contain literal versionCode and versionName assignments")
    return _version_code(int(code[1])), _version(name[1])


def _bump(core, change):
    major, minor, patch = core
    if change == "major":
        return major + 1, 0, 0
    if change == "minor":
        return major, minor + 1, 0
    return major, minor, patch + 1


def plan_release(state, gradle_text, change, changelog, force):
    if not isinstance(state, dict):
        raise ValueError("Release state must be a JSON object")
    if change not in {"major", "minor", "patch", "test"}:
        raise ValueError("Change must be major, minor, patch, or test")
    if type(force) is not bool:
        raise ValueError("force must be a boolean")
    local_code, (local_core, local_beta) = _gradle_version(gradle_text)
    codes = [local_code]
    channels = {}
    for channel in ("stable", "preview"):
        release = state.get(channel)
        if release is None:
            continue
        if not isinstance(release, dict):
            raise ValueError(f"{channel} state must be an object or null")
        codes.append(_version_code(release.get("version_code")))
        channels[channel] = _version(release.get("version_name"))
    stable = channels.get("stable")
    if stable and stable[1] is not None:
        raise ValueError("Stable channel must have a stable version name")
    version_code = _version_code(max(codes) + 1)

    # A local version ahead of the published stable is an already-prepared target.
    baseline = stable[0] if stable else local_core
    target = _bump(baseline, "patch" if change == "test" else change)
    if stable:
        target = max(target, local_core)
    elif local_beta is not None and change in {"patch", "test"}:
        target = local_core
    version_name = ".".join(map(str, target))
    if change == "test":
        previous = [(local_core, local_beta), channels.get("preview", (None, None))]
        beta = max((number for core, number in previous if core == target and number is not None), default=0) + 1
        version_name += f"-beta{beta}"

    return {
        "version_code": version_code,
        "version_name": version_name,
        "channel": "preview" if change == "test" else "stable",
        "changelog": _changelog(changelog),
        "force": force,
    }


def write_metadata(plan, apk, output_dir):
    if not isinstance(plan, dict):
        raise ValueError("Plan must be a JSON object")
    code = _version_code(plan.get("version_code"))
    _, beta = _version(plan.get("version_name"))
    if "-release-beta" in plan["version_name"] or "-debug" in plan["version_name"]:
        raise ValueError("Legacy preview plan version is not supported; replan and rebuild the APK with X.Y.Z-betaN")
    channel = plan.get("channel")
    if channel not in {"stable", "preview"} or (channel == "preview") != (beta is not None):
        raise ValueError("Plan channel and version name disagree")
    if type(plan.get("force")) is not bool:
        raise ValueError("force must be a boolean")
    changelog = _changelog(plan.get("changelog"))
    apk, output_dir = Path(apk), Path(output_dir)
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("APK must be a non-empty file")

    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / "ahutong.apk"
    if apk.resolve() != target.resolve():
        shutil.copyfile(apk, target)
    digest = hashlib.sha256()
    with target.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    metadata = {
        "version_code": code,
        "version_name": plan["version_name"],
        "channel": channel,
        "changelog": changelog,
        "force": plan["force"],
        "apk": "ahutong.apk",
        "sha256": digest.hexdigest(),
        "size": target.stat().st_size,
    }
    files = {
        "apk_version.txt": str(code),
        "apk_version_name.txt": metadata["version_name"],
        "apk_changelog.txt": changelog,
        "apk_force.txt": str(metadata["force"]).lower(),
    }
    for name, text in files.items():
        (output_dir / name).write_text(text + "\n", encoding="utf-8")
    (output_dir / "release.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return metadata


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("plan")
    plan.add_argument("--state", type=Path, required=True)
    plan.add_argument("--gradle", type=Path, required=True)
    plan.add_argument("--change", choices=("major", "minor", "patch", "test"), required=True)
    plan.add_argument("--changelog-file", type=Path, required=True)
    plan.add_argument("--force", choices=("true", "false"), required=True)
    plan.add_argument("--output", type=Path, required=True)
    write = commands.add_parser("write")
    write.add_argument("--plan", type=Path, required=True)
    write.add_argument("--apk", type=Path, required=True)
    write.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "plan":
            result = plan_release(
                json.loads(args.state.read_text(encoding="utf-8")),
                args.gradle.read_text(encoding="utf-8"), args.change,
                args.changelog_file.read_text(encoding="utf-8"), args.force == "true",
            )
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        else:
            write_metadata(json.loads(args.plan.read_text(encoding="utf-8")), args.apk, args.output_dir)
    except (ValueError, OSError) as error:
        parser.exit(2, f"error: {error}\n")


if __name__ == "__main__":
    main()
