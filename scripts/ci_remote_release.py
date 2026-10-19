#!/usr/bin/env python3
"""Validate and deploy one APK channel; only cooperating CI writers share this lock.

Each file is replaced atomically, with rollback on failure. Readers of the existing
update server do not take this lock, so the set of files is not an atomic snapshot.
"""

from __future__ import annotations

import argparse
import contextlib
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
import uuid
import zipfile


APK = "ahutong.apk"
VERSION = "apk_version.txt"
VERSION_NAME = "apk_version_name.txt"
CHANGELOG = "apk_changelog.txt"
FORCE = "apk_force.txt"
RELEASE = "release.json"
EXPECTED = "expected-state.json"
DEPLOY_FILES = (APK, VERSION_NAME, CHANGELOG, FORCE, RELEASE, VERSION)
BUNDLE_FILES = frozenset((*DEPLOY_FILES, EXPECTED))
MAX_PAYLOAD = 2 * 1024**3
MAX_METADATA = 1024**2
MAX_VERSION_CODE = 2100000000
CHUNK = 1024**2


class ReleaseError(ValueError):
    """Invalid release inputs or server state; no publication should proceed."""


def _root(path: str | Path) -> Path:
    root = Path(path).resolve(strict=True)
    if not root.is_dir():
        raise ReleaseError("Release root must be an existing directory")
    return root


def _directory(path: Path) -> bool:
    if path.is_symlink():
        raise ReleaseError(f"Refusing symlink directory: {path.name}")
    if path.exists() and not path.is_dir():
        raise ReleaseError(f"Expected directory: {path.name}")
    return path.exists()


def _regular(path: Path) -> bool:
    try:
        mode = path.lstat().st_mode
    except FileNotFoundError:
        return False
    if not stat.S_ISREG(mode):
        raise ReleaseError(f"Expected regular file: {path.name}")
    return True


@contextlib.contextmanager
def _locked(root: Path):
    descriptor = os.open(
        root / ".ci-release.lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600
    )
    try:
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise ReleaseError("Release lock must be a regular file")
        fcntl.flock(descriptor, fcntl.LOCK_EX)
        yield
    finally:
        os.close(descriptor)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _text(path: Path) -> str:
    if path.stat().st_size > MAX_METADATA:
        raise ReleaseError(f"Metadata is too large: {path.name}")
    return path.read_text(encoding="utf-8").strip()


def _code(value, label: str) -> int:
    if type(value) is not int or not 1 <= value <= MAX_VERSION_CODE:
        raise ReleaseError(f"{label} must be an integer between 1 and {MAX_VERSION_CODE}")
    return value


def _channel(root: Path, channel: str) -> Path:
    return root if channel == "stable" else root / "preview"


def _state(root: Path) -> dict:
    result = {}
    files = []
    for channel in ("stable", "preview"):
        directory = _channel(root, channel)
        _directory(directory)
        present = {}
        for name in DEPLOY_FILES:
            path = directory / name
            present[name] = _regular(path)
            files.append({
                "path": f"{channel}/{name}",
                "sha256": _sha256(path) if present[name] else None,
            })
        if present[VERSION]:
            try:
                version_code = _code(int(_text(directory / VERSION)), "Stored version code")
            except ValueError as error:
                raise ReleaseError(f"Invalid {channel} version code") from error
            version_name = _text(directory / VERSION_NAME) if present[VERSION_NAME] else str(version_code)
            result[channel] = {"version_code": version_code, "version_name": version_name}
        else:
            result[channel] = None
    encoded = json.dumps(files, sort_keys=True, separators=(",", ":")).encode()
    result["fingerprint"] = hashlib.sha256(encoded).hexdigest()
    return result


def state(root: str | Path) -> dict:
    root = _root(root)
    with _locked(root):
        return _state(root)


def _json(path: Path) -> dict:
    try:
        value = json.loads(_text(path))
    except (UnicodeError, ValueError) as error:
        raise ReleaseError(f"Invalid JSON: {path.name}") from error
    if not isinstance(value, dict):
        raise ReleaseError(f"JSON must be an object: {path.name}")
    return value


def _extract(bundle: Path, staging: Path) -> tuple[dict, str]:
    try:
        with zipfile.ZipFile(bundle) as archive:
            infos = archive.infolist()
            if len(infos) != len(BUNDLE_FILES) or {info.filename for info in infos} != BUNDLE_FILES:
                raise ReleaseError("Bundle must contain each of the seven allowed files exactly once")
            total = 0
            for info in infos:
                mode = info.external_attr >> 16
                if (info.is_dir() or stat.S_IFMT(mode) not in (0, stat.S_IFREG)
                        or info.flag_bits & 1):
                    raise ReleaseError(f"Unsupported ZIP entry: {info.filename}")
                limit = MAX_PAYLOAD if info.filename == APK else MAX_METADATA
                if info.file_size < 0 or info.file_size > limit:
                    raise ReleaseError(f"ZIP entry is too large: {info.filename}")
                total += info.file_size
                if total > MAX_PAYLOAD:
                    raise ReleaseError("Bundle payload exceeds 2 GiB")
            for info in infos:
                written = 0
                with archive.open(info) as source, (staging / info.filename).open("xb") as target:
                    while True:
                        chunk = source.read(CHUNK)
                        if not chunk:
                            break
                        written += len(chunk)
                        if written > info.file_size:
                            raise ReleaseError("ZIP payload exceeds its declared size")
                        target.write(chunk)
                    if info.filename in DEPLOY_FILES:
                        os.fchmod(target.fileno(), 0o644)
                    target.flush()
                    os.fsync(target.fileno())
                if written != info.file_size:
                    raise ReleaseError("ZIP payload does not match its declared size")
    except (zipfile.BadZipFile, RuntimeError, NotImplementedError) as error:
        raise ReleaseError("Invalid or unsupported ZIP bundle") from error

    plan = _json(staging / RELEASE)
    if plan.get("channel") not in ("stable", "preview"):
        raise ReleaseError("Plan channel must be stable or preview")
    code = _code(plan.get("version_code"), "Plan version code")
    name = plan.get("version_name")
    if not isinstance(name, str) or not name or name != name.strip() or "\n" in name or "\r" in name:
        raise ReleaseError("Plan version name must be a nonempty single line")
    if _text(staging / VERSION) != str(code) or _text(staging / VERSION_NAME) != name:
        raise ReleaseError("APK version metadata disagrees with the plan")
    force = _text(staging / FORCE).lower()
    if force not in ("0", "1", "false", "true"):
        raise ReleaseError("APK force metadata must be 0, 1, false or true")
    if "force" in plan and (type(plan["force"]) is not bool or plan["force"] != (force in ("1", "true"))):
        raise ReleaseError("APK force metadata disagrees with the plan")
    if "changelog" in plan and plan["changelog"] != _text(staging / CHANGELOG):
        raise ReleaseError("APK changelog metadata disagrees with the plan")
    apk = staging / APK
    if type(plan.get("size")) is not int or plan["size"] <= 0 or apk.stat().st_size != plan["size"]:
        raise ReleaseError("APK size disagrees with the plan")
    if plan.get("sha256") != _sha256(apk):
        raise ReleaseError("APK SHA-256 disagrees with the plan")
    expected = _json(staging / EXPECTED).get("fingerprint")
    if not isinstance(expected, str) or len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected):
        raise ReleaseError("Expected state must contain a SHA-256 fingerprint")
    return plan, expected


def _sync_directory(directory: Path):
    descriptor = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _backup(root: Path, target: Path, channel: str, previous: dict | None) -> tuple[Path, dict]:
    parent = root / ".ci-backups"
    _directory(parent)
    parent.mkdir(mode=0o700, exist_ok=True)
    parent = parent / channel
    _directory(parent)
    parent.mkdir(mode=0o700, exist_ok=True)
    old_code = previous["version_code"] if previous else "none"
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    backup = parent / f"{old_code}-{timestamp}-{uuid.uuid4().hex}"
    backup.mkdir(mode=0o700)
    originals = {}
    for name in DEPLOY_FILES:
        originals[name] = _regular(target / name)
        if originals[name]:
            shutil.copy2(target / name, backup / name)
            with (backup / name).open("rb") as stream:
                os.fsync(stream.fileno())
    manifest = {"channel": channel, "previous": previous, "original_files": originals}
    with (backup / "backup.json").open("x", encoding="utf-8") as stream:
        json.dump(manifest, stream, sort_keys=True)
        stream.flush()
        os.fsync(stream.fileno())
    _sync_directory(backup)
    return backup, originals


def _restore(target: Path, backup: Path, originals: dict, staging: Path):
    for name in DEPLOY_FILES:
        destination = target / name
        if originals[name]:
            restored = staging / f"restore-{name}"
            shutil.copy2(backup / name, restored)
            os.replace(restored, destination)
        elif destination.exists():
            if not _regular(destination):
                raise ReleaseError(f"Cannot remove non-file during rollback: {name}")
            destination.unlink()
    _sync_directory(target)


def publish(root: str | Path, bundle: str | Path) -> dict:
    root = _root(root)
    with _locked(root), tempfile.TemporaryDirectory(prefix=".ci-stage-", dir=root) as temporary:
        staging = Path(temporary)
        plan, expected = _extract(Path(bundle), staging)
        before = _state(root)
        if expected != before["fingerprint"]:
            raise ReleaseError("Server release state changed since the build plan; rebuild before publishing")
        max_code = max((value["version_code"] for key, value in before.items()
                        if key != "fingerprint" and value is not None), default=0)
        if plan["version_code"] <= max_code:
            raise ReleaseError("Version code must exceed both existing channels")
        channel = plan["channel"]
        target = _channel(root, channel)
        _directory(target)
        target.mkdir(exist_ok=True)
        backup, originals = _backup(root, target, channel, before[channel])
        try:
            for name in DEPLOY_FILES:
                os.replace(staging / name, target / name)
            _sync_directory(target)
            after = _state(root)
        except BaseException as error:
            try:
                _restore(target, backup, originals, staging)
            except BaseException as rollback_error:
                raise ReleaseError(f"Publication and rollback failed; recover from {backup}: {rollback_error}") from error
            raise ReleaseError(f"Publication failed and original files were restored; backup: {backup}") from error
        return {
            "channel": channel,
            "version_code": plan["version_code"],
            "version_name": plan["version_name"],
            "backup": str(backup),
            "fingerprint": after["fingerprint"],
        }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    snapshot = commands.add_parser("state")
    snapshot.add_argument("--root", required=True)
    deployment = commands.add_parser("publish")
    deployment.add_argument("--root", required=True)
    deployment.add_argument("--bundle", required=True)
    args = parser.parse_args(argv)
    try:
        result = state(args.root) if args.command == "state" else publish(args.root, args.bundle)
    except (ReleaseError, OSError, UnicodeError) as error:
        print(f"Release rejected: {error}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
