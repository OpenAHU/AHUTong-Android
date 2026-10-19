#!/usr/bin/env python3
"""Validate release artifacts and prepare Sentry inputs without third-party packages."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import Request, urlopen
import uuid
import zipfile


PACKAGE = "com.ahu.ahutong"
PLUGIN_VERSION = "6.21.0"
CLI_VERSION = "3.7.0"
PLUGIN_SHA256 = "5f71085972987e8300531d2df92f399cc482dc1c96a9fef064672a835ab79e96"
UUID_KEY = "io.sentry.ProguardUuids"
MAPPING_ATTEMPTS = 5


class ValidationError(Exception):
    pass


def read_json(path):
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise ValidationError(f"Cannot read JSON: {Path(path).name}") from error
    if not isinstance(value, dict):
        raise ValidationError(f"Expected JSON object: {Path(path).name}")
    return value


def require_file(path):
    path = Path(path)
    if not path.is_file() or not path.stat().st_size:
        raise ValidationError(f"Missing or empty file: {path.name}")
    return path


def sha256(path):
    digest = hashlib.sha256()
    with require_file(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(command):
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.SubprocessError) as error:
        # Tool output can include certificate DNs or authentication diagnostics.
        raise ValidationError(f"Command failed: {Path(command[0]).name}") from error
    return result.stdout


def proguard_uuid(text):
    values = []
    for line in text.splitlines():
        key, separator, value = line.strip().partition("=")
        if separator and key == UUID_KEY:
            values.append(value.strip())
    try:
        if len(values) != 1:
            raise ValueError("Expected one ProGuard UUID")
        return str(uuid.UUID(values[0]))
    except ValueError as error:
        raise ValidationError("Invalid or missing ProGuard UUID") from error


def apk_identity(apk, aapt2):
    output = run([str(aapt2), "dump", "badging", str(apk)])
    try:
        line = next(line for line in output.splitlines() if line.startswith("package: "))
        fields = dict(item.split("=", 1) for item in shlex.split(line)[1:] if "=" in item)
        identity = {
            "package": fields["name"],
            "version_name": fields["versionName"],
            "version_code": int(fields["versionCode"]),
        }
    except (StopIteration, KeyError, ValueError) as error:
        raise ValidationError("Cannot read final APK package/version") from error
    return identity


def verify_signature(apk, apksigner):
    pin = os.environ.get("ANDROID_CERT_SHA256", "").strip().replace(":", "").lower()
    if not re.fullmatch(r"[a-f0-9]{64}", pin):
        raise ValidationError("ANDROID_CERT_SHA256 must contain a SHA-256 certificate pin")
    output = run([str(apksigner), "verify", "--print-certs", str(apk)])
    certificates = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([A-Fa-f0-9:]+)\s*$", output, re.MULTILINE
    )
    if len(certificates) != 1 or certificates[0].replace(":", "").lower() != pin:
        raise ValidationError("APK signing certificate does not match ANDROID_CERT_SHA256")


def prepare(args):
    apk, mapping, uuid_file = (require_file(value) for value in (args.apk, args.mapping, args.uuid_file))
    plan = read_json(args.plan)
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        raise ValidationError("ANDROID_SDK_ROOT or ANDROID_HOME is required")
    tools = Path(sdk) / "build-tools" / "37.0.0"
    identity = apk_identity(apk, require_file(tools / "aapt2"))
    if identity["package"] != PACKAGE:
        raise ValidationError("Unexpected APK package")
    if (type(plan.get("version_code")) is not int or plan["version_code"] <= 0
            or identity["version_name"] != plan.get("version_name")
            or identity["version_code"] != plan.get("version_code")):
        raise ValidationError("APK version does not match release plan")
    if plan.get("channel") not in {"stable", "preview"}:
        raise ValidationError("Invalid release plan channel")
    verify_signature(apk, require_file(tools / "apksigner"))
    generated_uuid = proguard_uuid(uuid_file.read_text(encoding="utf-8"))
    try:
        with zipfile.ZipFile(apk) as archive:
            embedded_uuid = proguard_uuid(archive.read("assets/sentry-debug-meta.properties").decode("utf-8"))
    except (OSError, KeyError, UnicodeError, zipfile.BadZipFile) as error:
        raise ValidationError("APK has no readable Sentry debug metadata") from error
    if generated_uuid != embedded_uuid:
        raise ValidationError("APK and generated ProGuard UUIDs differ")

    gradle_home = Path(args.gradle_home or os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    cache = gradle_home / "caches/modules-2/files-2.1/io.sentry/sentry-android-gradle-plugin" / PLUGIN_VERSION
    jars = sorted(cache.glob(f"*/sentry-android-gradle-plugin-{PLUGIN_VERSION}.jar"))
    if not jars or any(sha256(jar) != PLUGIN_SHA256 for jar in jars):
        raise ValidationError("Missing or unverified Sentry Gradle plugin JAR")
    try:
        with zipfile.ZipFile(jars[0]) as archive:
            cli_bytes = archive.read("bin/sentry-cli-Linux-x86_64")
    except (OSError, KeyError, zipfile.BadZipFile) as error:
        raise ValidationError("Sentry plugin JAR has no Linux x86_64 CLI") from error
    bundle = Path(args.output_dir)
    bundle.mkdir(parents=True, exist_ok=True)
    cli = bundle / "sentry-cli"
    cli.write_bytes(cli_bytes)
    cli.chmod(0o700)
    if run([str(cli.resolve()), "--version"]).strip() != f"sentry-cli {CLI_VERSION}":
        raise ValidationError("Unexpected bundled Sentry CLI version")
    manifest = {
        **identity,
        "channel": plan["channel"],
        "release": f"{identity['package']}@{identity['version_name']}+{identity['version_code']}",
        "uuid": embedded_uuid,
        "apk_sha256": sha256(apk),
        "mapping_sha256": sha256(mapping),
        "cli_sha256": sha256(cli),
    }
    for source, name in ((mapping, "mapping.txt"), (uuid_file, "uuid.properties")):
        if source.resolve() != (bundle / name).resolve():
            shutil.copyfile(source, bundle / name)
    (bundle / "sentry.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def sentry_context():
    values = [os.environ.get(key, "").strip() for key in ("SENTRY_AUTH_TOKEN", "SENTRY_ORG", "SENTRY_PROJECT")]
    if not all(values):
        raise ValidationError("SENTRY_AUTH_TOKEN, SENTRY_ORG and SENTRY_PROJECT are required")
    return values


def get_json(url, token):
    request = Request(url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json"}, method="GET")
    with urlopen(request, timeout=10) as response:
        return json.load(response)


def verify_mapping(args):
    manifest = read_json(args.manifest)
    try:
        mapping_uuid = str(uuid.UUID(manifest["uuid"]))
    except (KeyError, ValueError, TypeError, AttributeError) as error:
        raise ValidationError("Invalid mapping UUID in Sentry manifest") from error
    token, org, project = sentry_context()
    url = (f"https://sentry.io/api/0/projects/{quote(org, safe='')}/{quote(project, safe='')}/files/dsyms/?"
           + urlencode({"debug_id": mapping_uuid}))
    for attempt in range(MAPPING_ATTEMPTS):
        try:
            files = get_json(url, token)
            if not isinstance(files, list):
                raise ValidationError("Unexpected Sentry debug files response")
            for item in files:
                if not isinstance(item, dict) or item.get("symbolType") != "proguard":
                    continue
                try:
                    identifier = str(uuid.UUID(item.get("debugId") or item.get("uuid", "")))
                except (ValueError, TypeError, AttributeError):
                    continue
                if identifier == mapping_uuid:
                    return
        except HTTPError as error:
            if error.code not in {429, 500, 502, 503, 504}:
                raise ValidationError(f"Sentry mapping lookup failed: HTTP {error.code}") from error
        except (URLError, TimeoutError, OSError):
            pass
        except ValueError as error:
            raise ValidationError("Invalid Sentry debug files JSON") from error
        if attempt + 1 < MAPPING_ATTEMPTS:
            time.sleep(1)
    raise ValidationError("Matching ProGuard mapping was not available within the retry limit")


def record(args):
    manifest = read_json(args.manifest)
    release_info = read_json(args.release_info)
    if (manifest.get("package") != PACKAGE
            or manifest.get("channel") not in {"stable", "preview"}
            or type(manifest.get("version_code")) is not int
            or manifest["version_code"] <= 0
            or not isinstance(manifest.get("version_name"), str)
            or not manifest["version_name"]
            or not isinstance(manifest.get("apk_sha256"), str)
            or not re.fullmatch(r"[a-f0-9]{64}", manifest["apk_sha256"])):
        raise ValidationError("Invalid Sentry artifact identity")
    if any(release_info.get(key) != manifest[key] for key in ("version_code", "version_name", "channel")):
        raise ValidationError("Published release metadata does not match Sentry artifact")
    expected_release = f"{PACKAGE}@{manifest['version_name']}+{manifest['version_code']}"
    if manifest.get("release") != expected_release or release_info.get("sha256") != manifest.get("apk_sha256"):
        raise ValidationError("Published APK does not match Sentry artifact")
    cli = require_file(args.cli)
    if sha256(cli) != manifest.get("cli_sha256"):
        raise ValidationError("Sentry CLI checksum does not match artifact")
    # GitHub artifact downloads do not preserve the executable permission.
    cli.chmod(0o700)
    token, org, _ = sentry_context()
    server = os.environ.get("GITHUB_SERVER_URL", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    parsed = urlsplit(server)
    if (parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password
            or parsed.query or parsed.fragment
            or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository)
            or not re.fullmatch(r"\d+", run_id)):
        raise ValidationError("GitHub workflow identity is required for deployment record")
    workflow_url = f"{server.rstrip('/')}/{repository}/actions/runs/{run_id}"
    command = str(cli.resolve())
    run([command, "releases", "finalize", expected_release])
    url = (f"https://sentry.io/api/0/organizations/{quote(org, safe='')}/releases/"
           f"{quote(expected_release, safe='')}/deploys/?per_page=100")
    try:
        deployments = get_json(url, token)
    except HTTPError as error:
        raise ValidationError(f"Sentry deployment lookup failed: HTTP {error.code}") from error
    except (URLError, TimeoutError, OSError, ValueError) as error:
        raise ValidationError("Sentry deployment lookup failed") from error
    if not isinstance(deployments, list):
        raise ValidationError("Unexpected Sentry deployments response")
    name = f"{manifest['channel']}-{manifest['version_code']}"
    if any(item.get("environment") == "release" and item.get("name") == name
           for item in deployments if isinstance(item, dict)):
        return
    run([command, "deploys", "new", "--release", expected_release,
         "--env", "release", "--name", name, "--url", workflow_url])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    prepare_parser = sub.add_parser("prepare")
    for name in ("apk", "mapping", "uuid-file", "plan", "output-dir"):
        prepare_parser.add_argument(f"--{name}", required=True)
    prepare_parser.add_argument("--gradle-home")
    verify_parser = sub.add_parser("verify-mapping")
    verify_parser.add_argument("--manifest", required=True)
    record_parser = sub.add_parser("record")
    for name in ("manifest", "release-info", "cli"):
        record_parser.add_argument(f"--{name}", required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "prepare":
            prepare(args)
        elif args.command == "verify-mapping":
            verify_mapping(args)
        else:
            record(args)
    except (ValidationError, OSError, UnicodeError) as error:
        print(f"Sentry CI validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
