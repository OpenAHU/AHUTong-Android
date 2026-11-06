#!/usr/bin/env python3
"""Publish a verified APK payload to GitHub without replacing an existing release's bytes."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile

from ci_release import _changelog, _version, _version_code


FILES = ("ahutong.apk", "ahutong.apk.sha256")
FIELDS = {"version_code", "version_name", "channel", "changelog", "force", "apk", "sha256", "size"}
API_VERSION = "2022-11-28"


class ReleaseError(ValueError):
    pass


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def regular_file(path):
    path = Path(path)
    if not stat.S_ISREG(path.lstat().st_mode) or not path.stat().st_size:
        raise ReleaseError(f"Expected non-empty regular file: {path.name}")
    return path


def text(path):
    path = regular_file(path)
    if path.stat().st_size > 1024 * 1024:
        raise ReleaseError(f"Metadata is too large: {path.name}")
    return path.read_text(encoding="utf-8")


def load_payload(directory):
    directory = Path(directory).resolve(strict=True)
    metadata = json.loads(text(directory / "release.json"))
    if not isinstance(metadata, dict) or set(metadata) != FIELDS:
        raise ReleaseError("release.json must contain only the public release metadata fields")
    _version_code(metadata["version_code"])
    core, beta = _version(metadata["version_name"])
    canonical = ".".join(map(str, core)) + (f"-beta{beta}" if beta is not None else "")
    if metadata["version_name"] != canonical:
        raise ReleaseError("Release requires a canonical X.Y.Z or X.Y.Z-betaN version name")
    if (not isinstance(metadata["channel"], str) or metadata["channel"] not in {"stable", "preview"}
            or (metadata["channel"] == "preview") != (beta is not None)):
        raise ReleaseError("Release channel and version name disagree")
    if type(metadata["force"]) is not bool or metadata["apk"] != "ahutong.apk":
        raise ReleaseError("Invalid force flag or APK filename")
    if not isinstance(metadata["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", metadata["sha256"]):
        raise ReleaseError("Invalid APK sha256")
    if type(metadata["size"]) is not int or not 0 < metadata["size"] < 2 * 1024**3:
        raise ReleaseError("APK size must be a positive integer below 2 GiB")
    apk = regular_file(directory / "ahutong.apk")
    if apk.stat().st_size != metadata["size"] or sha256(apk) != metadata["sha256"]:
        raise ReleaseError("APK size or sha256 does not match release.json")
    changelog = _changelog(metadata["changelog"])
    if _changelog(text(directory / "apk_changelog.txt")) != changelog:
        raise ReleaseError("Changelog does not match release.json")
    # These are emitted by ci_release.py too; check them when present in the downloaded payload.
    for name, expected in (("apk_version.txt", str(metadata["version_code"])),
                           ("apk_version_name.txt", metadata["version_name"]),
                           ("apk_force.txt", str(metadata["force"]).lower())):
        path = directory / name
        if path.exists() and text(path).strip() != expected:
            raise ReleaseError(f"{name} does not match release.json")
    checksum = directory / "ahutong.apk.sha256"
    if checksum.is_symlink() or (checksum.exists() and not checksum.is_file()):
        raise ReleaseError("Checksum output must be a regular file")
    checksum.write_text(f"{metadata['sha256']}  ahutong.apk\n", encoding="ascii")
    return metadata, changelog, {name: directory / name for name in FILES}


class GitHub:
    def __init__(self, repository):
        self.repository = repository
        self.prefix = f"repos/{repository}"

    def api(self, endpoint, method="GET", body=None, missing=False):
        command = ["gh", "api", f"{self.prefix}/{endpoint}", "--hostname", "github.com", "--method", method,
                   "--header", "Accept: application/vnd.github+json",
                   "--header", f"X-GitHub-Api-Version: {API_VERSION}"]
        if body is not None:
            command += ["--input", "-"]
        result = self.run(command, input=json.dumps(body) if body is not None else None)
        if result.returncode:
            if missing and re.search(r"\(HTTP 404\)", result.stderr or ""):
                return None
            # Authentication/HTTP diagnostics can contain credentials or private links.
            raise ReleaseError(f"GitHub API failed: {method} {endpoint}")
        try:
            return json.loads(result.stdout)
        except ValueError as error:
            raise ReleaseError(f"GitHub API returned invalid JSON: {endpoint}") from error

    @staticmethod
    def run(command, **kwargs):
        try:
            return subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                                  timeout=600, **kwargs)
        except (OSError, subprocess.SubprocessError) as error:
            raise ReleaseError("GitHub CLI failed; check runner authentication and connectivity") from error

    def pages(self, endpoint):
        result = []
        page = 1
        while True:
            values = self.api(f"{endpoint}?per_page=100&page={page}")
            if not isinstance(values, list):
                raise ReleaseError(f"Expected a GitHub list: {endpoint}")
            result.extend(values)
            if len(values) < 100:
                return result
            page += 1

    def release(self, tag):
        # List also includes authenticated drafts, so a failed upload can be resumed.
        releases = [release for release in self.pages("releases") if release.get("tag_name") == tag]
        if len(releases) > 1:
            raise ReleaseError(f"Multiple releases use tag {tag}; refusing to choose one")
        return releases[0] if releases else None

    def tag_commit(self, tag):
        reference = self.api(f"git/ref/tags/{tag}", missing=True)
        if reference is None:
            return None
        if not isinstance(reference, dict) or reference.get("ref") != f"refs/tags/{tag}":
            raise ReleaseError(f"GitHub returned an ambiguous tag: {tag}")
        target = reference.get("object", {})
        seen = set()
        for _ in range(10):
            digest = target.get("sha")
            if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{40}", digest) or digest in seen:
                break
            if target.get("type") == "commit":
                return digest
            if target.get("type") != "tag":
                break
            seen.add(digest)
            target = self.api(f"git/tags/{digest}").get("object", {})
        raise ReleaseError(f"Tag does not resolve to a commit: {tag}")

    def assets(self, release_id):
        assets = {}
        for asset in self.pages(f"releases/{release_id}/assets"):
            name = asset.get("name")
            if name not in FILES or name in assets:
                raise ReleaseError("Existing release has unexpected or duplicate assets; refusing to publish")
            assets[name] = asset
        return assets

    def verify_asset(self, asset, local):
        if asset.get("state") != "uploaded" or asset.get("size") != local.stat().st_size:
            raise ReleaseError(f"Existing asset has different size or incomplete upload: {local.name}")
        expected = sha256(local)
        digest = asset.get("digest")
        if digest is not None:
            if digest != f"sha256:{expected}":
                raise ReleaseError(f"Existing asset has different sha256: {local.name}")
            return
        # Older assets may lack GitHub's digest field; compare downloaded bytes instead.
        asset_id = asset.get("id")
        if type(asset_id) is not int or asset_id <= 0:
            raise ReleaseError(f"Invalid asset identity: {local.name}")
        with tempfile.TemporaryDirectory(prefix="ahutong-github-asset-") as folder:
            destination = Path(folder) / local.name
            command = ["gh", "api", f"{self.prefix}/releases/assets/{asset_id}", "--hostname", "github.com",
                       "--header", "Accept: application/octet-stream",
                       "--header", f"X-GitHub-Api-Version: {API_VERSION}"]
            try:
                with destination.open("wb") as output:
                    result = subprocess.run(command, stdout=output, stderr=subprocess.PIPE, timeout=600)
                if result.returncode or destination.stat().st_size != local.stat().st_size or sha256(destination) != expected:
                    raise ReleaseError(f"Existing asset bytes do not match: {local.name}")
            except (OSError, subprocess.SubprocessError) as error:
                raise ReleaseError(f"Could not verify existing asset: {local.name}") from error

    def upload(self, tag, path):
        # Never use --clobber: published bytes must not be silently replaced on retry.
        result = self.run(["gh", "release", "upload", tag, str(path), "--repo", f"github.com/{self.repository}"])
        if result.returncode:
            raise ReleaseError(f"Asset upload failed: {path.name}; rerun to resume the draft")


def check_release(release, tag, commit, preview, changelog):
    if (release.get("tag_name") != tag or release.get("prerelease") is not preview
            or release.get("name") != f"AHUTong {tag[1:]}"
            or release.get("target_commitish") != commit
            or release.get("body") != changelog):
        raise ReleaseError("Existing release version, channel or changelog differs; refusing to overwrite")
    if type(release.get("id")) is not int or release["id"] <= 0 or type(release.get("draft")) is not bool:
        raise ReleaseError("Invalid GitHub release identity or state")


def publish_release(payload_dir, repository, commit):
    if (not isinstance(repository, str) or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository)
            or any(part in {".", ".."} for part in repository.split("/"))):
        raise ReleaseError("Repository must be OWNER/REPO on github.com")
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-fA-F]{40}", commit):
        raise ReleaseError("Commit must be the full 40-character build commit SHA")
    commit = commit.lower()
    metadata, changelog, files = load_payload(payload_dir)
    tag = f"v{metadata['version_name']}"
    preview = metadata["channel"] == "preview"
    github = GitHub(repository)
    target = github.tag_commit(tag)
    if target is None:
        github.api("git/refs", "POST", {"ref": f"refs/tags/{tag}", "sha": commit})
    elif target != commit:
        raise ReleaseError(f"Tag {tag} points to a different build commit; refusing to move it")
    release = github.release(tag)
    reused = release is not None and not release.get("draft")
    if release is None:
        release = github.api("releases", "POST", {
            "tag_name": tag, "target_commitish": commit,
            "name": f"AHUTong {metadata['version_name']}", "body": changelog,
            "draft": True, "prerelease": preview, "make_latest": "false",
        })
    check_release(release, tag, commit, preview, changelog)
    assets = github.assets(release["id"])
    for name, asset in assets.items():
        github.verify_asset(asset, files[name])
    missing = [name for name in FILES if name not in assets]
    if missing and not release["draft"]:
        raise ReleaseError("Published release is incomplete; refusing to modify published assets")
    for name in missing:
        github.upload(tag, files[name])
    verified = github.assets(release["id"])
    if set(verified) != set(FILES):
        raise ReleaseError("Release is missing assets; draft was left unpublished")
    for name, asset in verified.items():
        github.verify_asset(asset, files[name])
    if github.tag_commit(tag) != commit:
        raise ReleaseError("Release tag changed during upload; refusing to publish")
    if release["draft"]:
        release = github.api(f"releases/{release['id']}", "PATCH", {
            "draft": False, "prerelease": preview, "make_latest": "false" if preview else "true",
        })
        check_release(release, tag, commit, preview, changelog)
        if release["draft"]:
            raise ReleaseError("GitHub did not publish the verified draft")
    return {"tag": tag, "url": f"https://github.com/{repository}/releases/tag/{tag}",
            "sha256": metadata["sha256"], "prerelease": preview, "reused": reused}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--payload-dir", type=Path, required=True)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args(argv)
    try:
        if not (os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")):
            raise ReleaseError("GH_TOKEN or GITHUB_TOKEN must be set for GitHub publication")
        result = publish_release(args.payload_dir, args.repo, args.commit)
    except (ValueError, OSError) as error:
        parser.exit(2, f"error: {error}\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
