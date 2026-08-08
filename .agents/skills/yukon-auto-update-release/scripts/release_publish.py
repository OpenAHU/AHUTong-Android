#!/usr/bin/env python3
from __future__ import annotations

import argparse
import configparser
from contextlib import contextmanager
from dataclasses import dataclass
import getpass
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import time
import uuid
from pathlib import Path
from typing import Any


SKILL_DIR = Path(__file__).resolve().parents[1]
AIO_CONFIG_RELATIVE = Path(".agents") / "skills" / "yukon-auto-update-release" / "config.local.json"
WORK_DIR = SKILL_DIR / ".work"

REMOTE_STATIC_DIR = "/home/ubuntu/AHUTong/server/update_server/static"
REMOTE_APK = f"{REMOTE_STATIC_DIR}/ahutong.apk"
REMOTE_APK_VERSION = f"{REMOTE_STATIC_DIR}/apk_version.txt"
REMOTE_APK_VERSION_NAME = f"{REMOTE_STATIC_DIR}/apk_version_name.txt"
REMOTE_CHANGELOG = f"{REMOTE_STATIC_DIR}/apk_changelog.txt"

CONFIG_FIELD_HELP = """Required config.local.json fields:
- keystore.path: absolute path to the signing .jks file
- keystore.store_password: keystore store password
- keystore.key_alias: signing key alias
- keystore.key_password: signing key password
- server.host: SSH server host
- server.port: SSH port, normally 22
- server.username: SSH username
- server.auth_method: password or private_key
- server.known_hosts_path: optional path to a separately verified SSH known_hosts file
- server.password: required when auth_method=password
- server.private_key_path: required when auth_method=private_key
- server.private_key_passphrase: optional when auth_method=private_key

These values are stored only in the ignored local file:
  <AIO-main>/.agents/skills/yukon-auto-update-release/config.local.json
"""

GENERIC_CHANGELOG_LINES = ["修复了一些 bug", "进行了一些体验优化"]
HIDDEN_CHANGELOG_KEYWORDS = [
    "gray",
    "grey",
    "rollout",
    "feature flag",
    "featureflag",
    "灰度",
    "debug",
    "首页编辑",
    "home edit",
    "edit rollout",
    "rollback",
    "downgrade",
    "回退",
    "回滚",
    "降级",
    "yukon-auto-update-release",
    ".agents/",
    "release_publish.py",
]


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class VersionPlan:
    code: int
    name: str
    rollback: bool
    same_version_hotfix: bool = False


def fail(message: str, code: int = 1) -> None:
    print(f"[ERROR] {message}", file=sys.stderr)
    raise SystemExit(code)


def no_interactive_input_error() -> ReleaseError:
    return ReleaseError(
        "Interactive input is not available. Ask the user to run --setup-config "
        "from a normal interactive terminal; do not collect secrets in chat.\n"
        f"{CONFIG_FIELD_HELP}"
    )


def should_prompt_for_missing_config() -> bool:
    if os.environ.get("CODEX_SHELL") == "1" or os.environ.get("CODEX_THREAD_ID"):
        return False
    return sys.stdin.isatty()


def missing_config_error(path: Path) -> ReleaseError:
    return ReleaseError(
        f"{path} does not exist.\n"
        "Ask the user to run this helper with --setup-config from a normal interactive "
        "terminal. Do not collect signing or server secrets in chat.\n"
        f"{CONFIG_FIELD_HELP}"
    )


def repo_root_from(start: Path) -> Path:
    current = start.resolve()
    for candidate in [current, *current.parents]:
        if (candidate / "settings.gradle.kts").exists() and (candidate / "app").is_dir():
            return candidate
    fail("Could not find the AHUTong Android subrepository root. Run from Android or pass --repo-root.")


def aio_has_android_submodule(path: Path) -> bool:
    modules_path = path / ".gitmodules"
    if not modules_path.is_file():
        return False
    parser = configparser.ConfigParser()
    parser.read(modules_path, encoding="utf-8")
    return any(
        section.startswith("submodule ")
        and parser.get(section, "path", fallback="") == "Android"
        for section in parser.sections()
    )


def aio_main_root_for(repo: Path) -> Path:
    configured_root = os.environ.get("AHUTONG_AIO_ROOT")
    if configured_root:
        aio_root = Path(configured_root).expanduser().resolve()
        if not aio_has_android_submodule(aio_root):
            raise ReleaseError(
                f"AHUTONG_AIO_ROOT is not an AHUTong AIO root with Android configured: {aio_root}"
            )
        return aio_root

    aio_worktree = repo.parent.resolve()
    if repo.resolve() != (aio_worktree / "Android").resolve() or not aio_has_android_submodule(aio_worktree):
        raise ReleaseError(
            "Android is not inside an AHUTong AIO worktree. Set AHUTONG_AIO_ROOT "
            "or pass --config with the canonical AIO config path."
        )

    result = subprocess.run(
        ["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
        cwd=str(aio_worktree),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        raise ReleaseError(f"Could not resolve AIO Git common directory: {result.stderr.strip()}")
    common_dir = Path(result.stdout.strip())
    if not common_dir.is_absolute():
        common_dir = (aio_worktree / common_dir).resolve()
    aio_root = common_dir.resolve().parent
    if common_dir.name != ".git" or not aio_has_android_submodule(aio_root):
        raise ReleaseError(f"Could not resolve the AIO main worktree from Git common dir: {common_dir}")
    return aio_root


def default_config_path(repo: Path) -> Path:
    return aio_main_root_for(repo) / AIO_CONFIG_RELATIVE


def prompt_required(label: str, secret: bool = False) -> str:
    while True:
        try:
            value = getpass.getpass(f"{label}: ") if secret else input(f"{label}: ")
        except (EOFError, KeyboardInterrupt) as exc:
            raise no_interactive_input_error() from exc
        value = value.strip()
        if value:
            return value
        print("Value is required.")


def prompt_optional_secret(label: str) -> str | None:
    try:
        value = getpass.getpass(f"{label} (blank if none): ").strip()
    except EOFError:
        return None
    except KeyboardInterrupt as exc:
        raise no_interactive_input_error() from exc
    return value or None


def prompt_optional(label: str) -> str | None:
    try:
        value = input(f"{label} (blank if none): ").strip()
    except EOFError:
        return None
    except KeyboardInterrupt as exc:
        raise no_interactive_input_error() from exc
    return value or None


def prompt_int(label: str) -> int:
    while True:
        value = prompt_required(label)
        try:
            return int(value)
        except ValueError:
            print("Enter an integer.")


def create_config_interactively() -> dict[str, Any]:
    auth_method = ""
    while auth_method not in {"password", "private_key"}:
        auth_method = prompt_required("server auth method (password/private_key)").lower()
        if auth_method not in {"password", "private_key"}:
            print("Enter password or private_key.")

    server: dict[str, Any] = {
        "host": prompt_required("server host"),
        "port": prompt_int("server port"),
        "username": prompt_required("server username"),
        "auth_method": auth_method,
        "known_hosts_path": prompt_optional("verified SSH known_hosts path"),
    }
    if auth_method == "password":
        server["password"] = prompt_required("server password", secret=True)
        server["private_key_path"] = None
        server["private_key_passphrase"] = None
    else:
        server["password"] = None
        server["private_key_path"] = prompt_required("server private key path")
        server["private_key_passphrase"] = prompt_optional_secret("server private key passphrase")

    return {
        "keystore": {
            "path": prompt_required("keystore path"),
            "store_password": prompt_required("keystore store password", secret=True),
            "key_alias": prompt_required("keystore key alias"),
            "key_password": prompt_required("keystore key password", secret=True),
        },
        "server": server,
    }


def validate_config(config: dict[str, Any]) -> None:
    keystore = config.get("keystore")
    server = config.get("server")
    if not isinstance(keystore, dict) or not isinstance(server, dict):
        raise ReleaseError("config.local.json must contain keystore and server objects.")

    for key in ["path", "store_password", "key_alias", "key_password"]:
        if not keystore.get(key):
            raise ReleaseError(f"Missing keystore.{key} in config.local.json.")

    if not Path(str(keystore["path"])).exists():
        raise ReleaseError(f"Keystore does not exist: {keystore['path']}")

    for key in ["host", "port", "username", "auth_method"]:
        if not server.get(key):
            raise ReleaseError(f"Missing server.{key} in config.local.json.")

    if server["auth_method"] not in {"password", "private_key"}:
        raise ReleaseError("server.auth_method must be password or private_key.")

    if server["auth_method"] == "password" and not server.get("password"):
        raise ReleaseError("Missing server.password in config.local.json.")

    if server["auth_method"] == "private_key":
        key_path = server.get("private_key_path")
        if not key_path:
            raise ReleaseError("Missing server.private_key_path in config.local.json.")
        if not Path(str(key_path)).exists():
            raise ReleaseError(f"Server private key does not exist: {key_path}")

    known_hosts_path = server.get("known_hosts_path")
    if known_hosts_path and not Path(str(known_hosts_path)).exists():
        raise ReleaseError(f"SSH known_hosts file does not exist: {known_hosts_path}")


def load_or_create_config(
    path: Path,
    dry_run: bool,
    allow_create: bool = False,
) -> dict[str, Any]:
    if path.exists():
        config = json.loads(path.read_text(encoding="utf-8"))
        validate_config(config)
        return config

    if not allow_create or not should_prompt_for_missing_config():
        raise missing_config_error(path)

    print(f"{path} does not exist. Enter release credentials for this machine.")
    print(CONFIG_FIELD_HELP)
    config = create_config_interactively()
    validate_config(config)
    if dry_run:
        print("[DRY-RUN] Config collected in memory only; config.local.json was not written.")
        return config

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(config, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass
    print(f"Wrote local credential config: {path}")
    return config


def import_paramiko():
    try:
        import paramiko  # type: ignore
    except ImportError as exc:
        raise ReleaseError("Python package paramiko is required for SSH/SFTP publishing.") from exc
    return paramiko


def ssh_connect(config: dict[str, Any]):
    paramiko = import_paramiko()
    server = config["server"]
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    if server.get("known_hosts_path"):
        client.load_host_keys(str(server["known_hosts_path"]))
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    kwargs: dict[str, Any] = {
        "hostname": server["host"],
        "port": int(server["port"]),
        "username": server["username"],
        "timeout": 20,
        "look_for_keys": False,
        "allow_agent": False,
    }
    if server["auth_method"] == "password":
        kwargs["password"] = server["password"]
    else:
        kwargs["key_filename"] = server["private_key_path"]
        if server.get("private_key_passphrase"):
            kwargs["passphrase"] = server["private_key_passphrase"]
    try:
        client.connect(**kwargs)
    except paramiko.SSHException as exc:
        client.close()
        raise ReleaseError(
            "SSH connection or host-key verification failed. Verify the server key in the "
            "system known_hosts file or server.known_hosts_path before retrying."
        ) from exc
    return client


def ssh_run(client: Any, command: str, check: bool = True) -> tuple[str, str, int]:
    stdin, stdout, stderr = client.exec_command(command, timeout=60)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    status = stdout.channel.recv_exit_status()
    if check and status != 0:
        raise ReleaseError(f"Remote command failed ({status}): {command}\n{err.strip()}")
    return out, err, status


def remote_read_text(client: Any, path: str, required: bool = False) -> str | None:
    out, _, _ = ssh_run(client, f"cat {shlex.quote(path)} 2>/dev/null || true", check=False)
    text = out.strip()
    if required and not text:
        raise ReleaseError(f"Remote file is missing or empty: {path}")
    return text or None


def parse_local_versions(repo: Path) -> tuple[int, str]:
    gradle_file = repo / "app" / "build.gradle.kts"
    text = gradle_file.read_text(encoding="utf-8")
    code_match = re.search(r"\bversionCode\s*=\s*(\d+)", text)
    name_match = re.search(r'\bversionName\s*=\s*"([^"]+)"', text)
    if not code_match or not name_match:
        raise ReleaseError("Could not parse versionCode/versionName from app/build.gradle.kts.")
    return int(code_match.group(1)), name_match.group(1)


def update_local_versions(repo: Path, version_code: int, version_name: str) -> None:
    gradle_file = repo / "app" / "build.gradle.kts"
    text = gradle_file.read_text(encoding="utf-8")
    text = re.sub(r"\bversionCode\s*=\s*\d+", f"versionCode = {version_code}", text, count=1)
    text = re.sub(r'\bversionName\s*=\s*"[^"]+"', f'versionName = "{version_name}"', text, count=1)
    gradle_file.write_text(text, encoding="utf-8")


@contextmanager
def temporary_local_versions(repo: Path, version_code: int, version_name: str):
    gradle_file = repo / "app" / "build.gradle.kts"
    original = gradle_file.read_bytes()
    update_local_versions(repo, version_code, version_name)
    try:
        yield
    finally:
        gradle_file.write_bytes(original)
        if gradle_file.read_bytes() != original:
            raise ReleaseError("Failed to restore app/build.gradle.kts after rollback publishing.")
        print("Restored app/build.gradle.kts after rollback publishing.")


def parse_version_tuple(version: str) -> tuple[int, ...]:
    if not re.fullmatch(r"\d+(?:\.\d+)*", version):
        raise ReleaseError(f"Version name is not numeric dot format: {version}")
    return tuple(int(part) for part in version.split("."))


def bump_patch(version: str) -> str:
    parts = list(parse_version_tuple(version))
    parts[-1] += 1
    return ".".join(str(part) for part in parts)


def max_version_name(local_name: str, server_name: str | None) -> str:
    if not server_name:
        return local_name
    return server_name if compare_version_names(server_name, local_name) > 0 else local_name


def compare_version_names(left: str, right: str) -> int:
    left_tuple = parse_version_tuple(left)
    right_tuple = parse_version_tuple(right)
    max_len = max(len(left_tuple), len(right_tuple))
    left_cmp = left_tuple + (0,) * (max_len - len(left_tuple))
    right_cmp = right_tuple + (0,) * (max_len - len(right_tuple))
    return (left_cmp > right_cmp) - (left_cmp < right_cmp)


def resolve_versions(
    local_code: int,
    local_name: str,
    server_code: int,
    server_name: str | None,
    explicit_code: int | None,
    explicit_name: str | None,
    on_mismatch: str,
) -> VersionPlan:
    mismatch = local_code != server_code or (server_name is not None and local_name != server_name)
    proposal_code = max(local_code, server_code) + 1
    proposal_name = bump_patch(max_version_name(local_name, server_name))
    published_name = server_name or local_name
    explicit_comparison = (
        compare_version_names(explicit_name, published_name)
        if explicit_name is not None
        else None
    )
    rollback = explicit_comparison is not None and explicit_comparison < 0
    same_version_hotfix = (
        explicit_comparison == 0
        and server_name is not None
    )

    if rollback:
        target_code = explicit_code if explicit_code is not None else server_code + 1
        if target_code <= server_code:
            raise ReleaseError("Rollback publish versionCode must be greater than server versionCode.")
        print(
            "Rollback publish requested: "
            f"server versionCode={server_code}, versionName={published_name}; "
            f"target versionCode={target_code}, versionName={explicit_name}"
        )
        return VersionPlan(code=target_code, name=explicit_name, rollback=True)

    if same_version_hotfix:
        if local_name != explicit_name:
            raise ReleaseError(
                "Same-version hotfix publishing requires the work branch versionName to match "
                f"the requested server version ({explicit_name}); local versionName is {local_name}."
            )
        target_code = explicit_code if explicit_code is not None else max(local_code, server_code) + 1
        if target_code <= max(local_code, server_code):
            raise ReleaseError(
                "Same-version hotfix versionCode must be greater than local and server versionCode."
            )
        print(
            "Same-version hotfix requested: "
            f"server versionCode={server_code}, versionName={published_name}; "
            f"target versionCode={target_code}, versionName={explicit_name}"
        )
        return VersionPlan(
            code=target_code,
            name=explicit_name,
            rollback=False,
            same_version_hotfix=True,
        )

    if mismatch:
        print("Local and server versions differ.")
        print(f"  local:  versionCode={local_code}, versionName={local_name}")
        print(f"  server: versionCode={server_code}, versionName={server_name or '(missing)'}")
        print("Choose one option before continuing:")
        print(f"  1. Upgrade both local and server to versionCode={proposal_code}, versionName={proposal_name}")
        print("  2. Abort, then rebase/sync to the latest version and rerun release publishing")
        if on_mismatch == "abort":
            raise SystemExit(2)
        if on_mismatch == "upgrade":
            return VersionPlan(code=proposal_code, name=proposal_name, rollback=False)
        while True:
            try:
                choice = input("Enter 1 or 2: ").strip()
            except (EOFError, KeyboardInterrupt) as exc:
                raise ReleaseError(
                    "Version mismatch requires an explicit user choice before continuing. "
                    "Ask the user to choose option 1 to upgrade both local and server, "
                    "or option 2 to abort and sync/rebase first."
                ) from exc
            if choice == "1":
                return VersionPlan(code=proposal_code, name=proposal_name, rollback=False)
            if choice == "2":
                raise SystemExit(2)
            print("Enter 1 or 2.")

    target_code = explicit_code if explicit_code is not None else proposal_code
    target_name = explicit_name if explicit_name is not None else bump_patch(local_name)
    if target_code <= max(local_code, server_code):
        raise ReleaseError("Target versionCode must be greater than local and server versionCode.")
    return VersionPlan(code=target_code, name=target_name, rollback=False)


def java_properties_unescape(value: str) -> str:
    return value.replace(r"\:", ":").replace(r"\\", "\\")


def android_sdk_dir(repo: Path) -> Path:
    local_props = repo / "local.properties"
    if local_props.exists():
        for line in local_props.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("sdk.dir="):
                return Path(java_properties_unescape(line.split("=", 1)[1]))
    for env_name in ["ANDROID_HOME", "ANDROID_SDK_ROOT"]:
        value = os.environ.get(env_name)
        if value:
            return Path(value)
    raise ReleaseError("Could not locate Android SDK from local.properties, ANDROID_HOME, or ANDROID_SDK_ROOT.")


def version_key(path: Path) -> tuple[int, ...]:
    return tuple(int(part) for part in re.findall(r"\d+", path.name))


def find_build_tool(repo: Path, tool_name: str) -> Path:
    sdk = android_sdk_dir(repo)
    build_tools = sdk / "build-tools"
    suffixes = [".bat", ".exe", ""]
    matches: list[Path] = []
    for directory in build_tools.iterdir():
        if not directory.is_dir():
            continue
        for suffix in suffixes:
            candidate = directory / f"{tool_name}{suffix}"
            if candidate.exists():
                matches.append(candidate)
                break
    if not matches:
        raise ReleaseError(f"Could not find {tool_name} in {build_tools}.")
    return sorted(matches, key=lambda item: version_key(item.parent), reverse=True)[0]


def run_cmd(
    cmd: list[str],
    cwd: Path,
    redacted: str | None = None,
    env: dict[str, str] | None = None,
) -> None:
    print(f"[RUN] {redacted or ' '.join(shlex.quote(part) for part in cmd)}")
    result = subprocess.run(cmd, cwd=str(cwd), env=env)
    if result.returncode != 0:
        raise ReleaseError(f"Command failed with exit code {result.returncode}.")


def git_capture(repo: Path, args: list[str], check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=str(repo),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and result.returncode != 0:
        raise ReleaseError(f"Git command failed: git {' '.join(args)}\n{result.stderr.strip()}")
    return result.stdout.strip()


def git_run(repo: Path, args: list[str]) -> None:
    print(f"[GIT] git {' '.join(shlex.quote(part) for part in args)}")
    result = subprocess.run(["git", *args], cwd=str(repo))
    if result.returncode != 0:
        raise ReleaseError(f"Git command failed with exit code {result.returncode}: git {' '.join(args)}")


def git_remote_exists(repo: Path, name: str) -> bool:
    result = subprocess.run(
        ["git", "remote", "get-url", name],
        cwd=str(repo),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return result.returncode == 0


def fetch_origin(repo: Path) -> None:
    if git_remote_exists(repo, "origin"):
        git_run(repo, ["fetch", "origin", "--prune"])
    else:
        print("No origin remote found; skipping fetch.")


def fetch_origin_branches(repo: Path, branches: list[str]) -> None:
    if not git_remote_exists(repo, "origin"):
        print("No origin remote found; skipping branch fetch.")
        return
    refspecs = [
        f"+refs/heads/{branch}:refs/remotes/origin/{branch}"
        for branch in dict.fromkeys(branches)
    ]
    git_run(repo, ["fetch", "origin", *refspecs])


def git_ref_exists(repo: Path, ref: str) -> bool:
    result = subprocess.run(
        ["git", "rev-parse", "--verify", "--quiet", ref],
        cwd=str(repo),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return result.returncode == 0


def git_resolve_ref(repo: Path, ref: str) -> str:
    return git_capture(repo, ["rev-parse", ref])


def git_is_ancestor(repo: Path, ancestor: str, descendant: str) -> bool:
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant],
        cwd=str(repo),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return result.returncode == 0


def release_branch(version_name: str) -> str:
    return f"release/{version_name}"


def require_clean_worktree(repo: Path) -> None:
    status = git_capture(repo, ["status", "--porcelain"])
    if status:
        raise ReleaseError(
            "Working tree must be clean before switching or creating release branches.\n"
            f"{status}"
        )


def ensure_release_branch(repo: Path, version_name: str, fallback_ref: str) -> str:
    branch = release_branch(version_name)
    local_ref = f"refs/heads/{branch}"
    remote_ref = f"refs/remotes/origin/{branch}"
    if git_ref_exists(repo, local_ref):
        print(f"Release branch exists locally: {branch}")
        return branch
    if git_ref_exists(repo, remote_ref):
        git_run(repo, ["branch", "--track", branch, f"origin/{branch}"])
        return branch
    if not git_ref_exists(repo, fallback_ref):
        raise ReleaseError(f"Cannot create {branch}; fallback ref does not exist: {fallback_ref}")
    git_run(repo, ["branch", branch, fallback_ref])
    print(f"Created release branch {branch} from {fallback_ref}.")
    return branch


def ensure_forward_release_branch(repo: Path, version_name: str, base_branch: str) -> str:
    branch = ensure_release_branch(repo, version_name, base_branch)
    base_commit = git_resolve_ref(repo, base_branch)
    branch_commit = git_resolve_ref(repo, branch)
    if branch_commit != base_commit:
        raise ReleaseError(
            f"{branch} points to {branch_commit[:7]}, but normal releases must be created "
            f"from current {base_branch} ({base_commit[:7]}). "
            "Repair the release branch or choose a new target version before publishing."
        )
    return branch


def release_branch_available(repo: Path, version_name: str) -> bool:
    branch = release_branch(version_name)
    return git_ref_exists(repo, f"refs/heads/{branch}") or git_ref_exists(repo, f"refs/remotes/origin/{branch}")


def ensure_existing_release_branch(repo: Path, version_name: str) -> str:
    branch = release_branch(version_name)
    local_ref = f"refs/heads/{branch}"
    remote_ref = f"refs/remotes/origin/{branch}"
    if git_ref_exists(repo, local_ref):
        print(f"Release branch exists locally: {branch}")
        return branch
    if git_ref_exists(repo, remote_ref):
        git_run(repo, ["branch", "--track", branch, f"origin/{branch}"])
        return branch
    raise ReleaseError(
        f"Rollback publish requires existing {branch} locally or on origin; "
        "refusing to create it from the current branch."
    )


def ensure_base_branch(repo: Path, base_branch: str) -> str:
    local_ref = f"refs/heads/{base_branch}"
    remote_ref = f"refs/remotes/origin/{base_branch}"
    if git_ref_exists(repo, local_ref):
        return base_branch
    if git_ref_exists(repo, remote_ref):
        git_run(repo, ["branch", "--track", base_branch, f"origin/{base_branch}"])
        return base_branch
    raise ReleaseError(f"Base branch does not exist locally or on origin: {base_branch}")


def same_version_hotfix_source_commits(
    repo: Path,
    target_version: str,
    base_branch: str,
) -> tuple[str, str, str, list[str]]:
    source_branch = git_capture(repo, ["branch", "--show-current"])
    target_branch = release_branch(target_version)
    if not source_branch:
        raise ReleaseError("Cannot publish from detached HEAD; checkout a named work branch first.")
    if source_branch in {base_branch, target_branch}:
        raise ReleaseError(
            "Same-version hotfix publishing must start from a dedicated work branch, not "
            f"{source_branch}."
        )

    base_ref = (
        f"refs/remotes/origin/{base_branch}"
        if git_ref_exists(repo, f"refs/remotes/origin/{base_branch}")
        else f"refs/heads/{base_branch}"
    )
    release_ref = f"refs/remotes/origin/{target_branch}"
    if not git_ref_exists(repo, base_ref):
        raise ReleaseError(f"Base branch does not exist locally or on origin: {base_branch}")
    if not git_ref_exists(repo, release_ref):
        raise ReleaseError(
            f"Same-version hotfix publishing requires existing origin/{target_branch}."
        )
    if not git_is_ancestor(repo, base_ref, source_branch):
        raise ReleaseError(
            f"{source_branch} must be a fast-forward descendant of origin/{base_branch} "
            "before same-version hotfix publishing."
        )

    commits_text = git_capture(repo, ["rev-list", "--reverse", f"{base_ref}..{source_branch}"])
    commits = [line for line in commits_text.splitlines() if line]
    if not commits:
        raise ReleaseError(
            f"{source_branch} has no commits to apply beyond origin/{base_branch}."
        )
    merge_commits = git_capture(
        repo,
        ["rev-list", "--merges", f"{base_ref}..{source_branch}"],
        check=False,
    )
    if merge_commits:
        raise ReleaseError(
            "Same-version hotfix source commits must be linear; merge commits cannot be "
            "cherry-picked automatically."
        )
    return source_branch, base_ref, release_ref, commits


def commit_version_bump_on_current_branch(
    repo: Path,
    version_code: int,
    version_name: str,
) -> str:
    require_clean_worktree(repo)
    update_local_versions(repo, version_code, version_name)
    changed = git_capture(repo, ["status", "--porcelain", "--", "app/build.gradle.kts"])
    if not changed:
        raise ReleaseError(
            f"Version metadata already records versionCode={version_code}, "
            f"versionName={version_name}; refusing to create an empty hotfix release commit."
        )
    git_run(repo, ["add", "app/build.gradle.kts"])
    git_run(repo, ["commit", "-m", f"chore(release): bump version to {version_name}"])
    return git_resolve_ref(repo, "HEAD")


def prepare_same_version_hotfix(
    repo: Path,
    target_version: str,
    target_code: int,
    base_branch: str,
) -> tuple[str, str]:
    require_clean_worktree(repo)
    fetch_origin(repo)
    fetch_origin_branches(repo, [base_branch, release_branch(target_version)])
    source_branch, _, release_ref, source_commits = same_version_hotfix_source_commits(
        repo,
        target_version,
        base_branch,
    )
    target_branch = release_branch(target_version)
    old_release_commit = git_resolve_ref(repo, release_ref)
    token = uuid.uuid4().hex[:12]
    prepared_base = f"release-prep-base-{token}"
    prepared_release = f"release-prep-hotfix-{token}"
    success = False

    try:
        git_run(repo, ["branch", prepared_base, source_branch])
        git_run(repo, ["checkout", prepared_base])
        version_commit = commit_version_bump_on_current_branch(
            repo,
            target_code,
            target_version,
        )

        git_run(repo, ["branch", prepared_release, release_ref])
        git_run(repo, ["checkout", prepared_release])
        for source_commit in source_commits:
            git_run(repo, ["cherry-pick", source_commit])
        git_run(repo, ["cherry-pick", version_commit])
        prepared_base_commit = git_resolve_ref(repo, prepared_base)
        prepared_release_commit = git_resolve_ref(repo, prepared_release)

        git_run(
            repo,
            [
                "push",
                "--atomic",
                "origin",
                f"{prepared_base_commit}:refs/heads/{base_branch}",
                f"{prepared_release_commit}:refs/heads/{target_branch}",
            ],
        )
        fetch_origin(repo)
        if git_ref_exists(repo, f"refs/heads/{base_branch}"):
            git_run(repo, ["branch", "-f", base_branch, f"origin/{base_branch}"])
        else:
            git_run(repo, ["branch", "--track", base_branch, f"origin/{base_branch}"])
        git_run(repo, ["checkout", "-B", target_branch, f"origin/{target_branch}"])
        success = True
        return old_release_commit, target_branch
    finally:
        if git_ref_exists(repo, "CHERRY_PICK_HEAD"):
            subprocess.run(["git", "cherry-pick", "--abort"], cwd=str(repo))
        current_branch = git_capture(repo, ["branch", "--show-current"], check=False)
        if not success and current_branch != source_branch:
            subprocess.run(["git", "checkout", source_branch], cwd=str(repo))
        for temporary_branch in (prepared_base, prepared_release):
            if (
                git_ref_exists(repo, f"refs/heads/{temporary_branch}")
                and git_capture(repo, ["branch", "--show-current"], check=False) != temporary_branch
            ):
                subprocess.run(
                    ["git", "branch", "-D", temporary_branch],
                    cwd=str(repo),
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )


def integrate_current_branch_into_base(repo: Path, base_branch: str) -> str:
    source_branch = git_capture(repo, ["branch", "--show-current"])
    if not source_branch:
        raise ReleaseError("Cannot publish from detached HEAD; checkout a named work branch first.")

    ensure_base_branch(repo, base_branch)
    if source_branch != base_branch:
        git_run(repo, ["checkout", base_branch])
        if git_ref_exists(repo, f"refs/remotes/origin/{base_branch}"):
            git_run(repo, ["merge", "--ff-only", f"origin/{base_branch}"])
        git_run(repo, ["merge", "--ff-only", source_branch])
    else:
        if git_ref_exists(repo, f"refs/remotes/origin/{base_branch}"):
            git_run(repo, ["merge", "--ff-only", f"origin/{base_branch}"])

    if git_remote_exists(repo, "origin"):
        git_run(repo, ["push", "origin", base_branch])
    return base_branch


def commit_base_version_bump(repo: Path, base_branch: str, version_code: int, version_name: str) -> str:
    current_branch = git_capture(repo, ["branch", "--show-current"])
    if current_branch != base_branch:
        git_run(repo, ["checkout", base_branch])
    require_clean_worktree(repo)

    update_local_versions(repo, version_code, version_name)
    changed = git_capture(repo, ["status", "--porcelain", "--", "app/build.gradle.kts"])
    if changed:
        git_run(repo, ["add", "app/build.gradle.kts"])
        git_run(repo, ["commit", "-m", f"chore(release): bump version to {version_name}"])
    else:
        print(f"{base_branch} already records versionCode={version_code}, versionName={version_name}.")

    if git_remote_exists(repo, "origin"):
        git_run(repo, ["push", "origin", base_branch])
    return git_resolve_ref(repo, base_branch)


def prepare_release_branches(
    repo: Path,
    previous_version: str | None,
    target_version: str,
    target_code: int | None,
    previous_release_ref: str,
    rollback: bool = False,
    same_version_hotfix: bool = False,
    base_branch: str = "master",
) -> tuple[str | None, str]:
    require_clean_worktree(repo)
    fetch_origin(repo)
    previous_branch = None
    if rollback:
        target_branch = ensure_existing_release_branch(repo, target_version)
        current_branch = git_capture(repo, ["branch", "--show-current"])
        if current_branch != target_branch:
            git_run(repo, ["checkout", target_branch])
        return None, target_branch

    if same_version_hotfix:
        if target_code is None:
            raise ReleaseError("Same-version hotfix releases require a target versionCode.")
        return prepare_same_version_hotfix(
            repo,
            target_version,
            target_code,
            base_branch,
        )

    if target_code is None:
        raise ReleaseError("Normal releases require a target versionCode before preparing release branches.")

    integrate_current_branch_into_base(repo, base_branch)
    commit_base_version_bump(repo, base_branch, target_code, target_version)
    if previous_version and previous_version != target_version and not rollback:
        previous_branch = ensure_release_branch(repo, previous_version, previous_release_ref)
    target_branch = ensure_forward_release_branch(repo, target_version, base_branch)
    current_branch = git_capture(repo, ["branch", "--show-current"])
    if current_branch != target_branch:
        git_run(repo, ["checkout", target_branch])
    if git_remote_exists(repo, "origin"):
        git_run(repo, ["push", "-u", "origin", target_branch])
    return previous_branch, target_branch


def require_pushed_release_branch(repo: Path, branch: str) -> str:
    if not git_remote_exists(repo, "origin"):
        raise ReleaseError("Production release requires an origin remote.")
    local_ref = f"refs/heads/{branch}"
    remote_ref = f"refs/remotes/origin/{branch}"
    if not git_ref_exists(repo, local_ref) or not git_ref_exists(repo, remote_ref):
        raise ReleaseError(f"Release branch must exist locally and on origin: {branch}")
    local_commit = git_resolve_ref(repo, local_ref)
    remote_commit = git_resolve_ref(repo, remote_ref)
    if local_commit != remote_commit:
        raise ReleaseError(
            f"{branch} is not synchronized with origin: local={local_commit}, remote={remote_commit}"
        )
    return local_commit


def ref_available(repo: Path, ref: str | None) -> bool:
    return bool(ref) and git_ref_exists(repo, ref)


def generic_changelog() -> str:
    return "\n".join(GENERIC_CHANGELOG_LINES) + "\n"


def contains_hidden_release_detail(text: str) -> bool:
    normalized = text.lower()
    return any(keyword.lower() in normalized for keyword in HIDDEN_CHANGELOG_KEYWORDS)


def safe_subject(subject: str) -> str | None:
    stripped = re.sub(r"^\w+(?:\([^)]+\))?!?:\s*", "", subject).strip()
    if not stripped or contains_hidden_release_detail(stripped):
        return None
    return stripped


def derive_changelog_from_release_diff(repo: Path, previous_branch: str | None, target_branch: str) -> str:
    if not ref_available(repo, previous_branch) or not ref_available(repo, target_branch):
        return generic_changelog()
    revision_range = f"{previous_branch}..{target_branch}"
    subjects_text = git_capture(repo, ["log", "--no-merges", "--format=%s", revision_range], check=False)
    files_text = git_capture(repo, ["diff", "--name-only", revision_range], check=False)
    if not subjects_text.strip() and not files_text.strip():
        return generic_changelog()

    visible_files = [
        path
        for path in files_text.splitlines()
        if path.strip() and not contains_hidden_release_detail(path)
    ]
    lines: list[str] = []
    for subject in subjects_text.splitlines():
        clean = safe_subject(subject)
        if clean and clean not in lines:
            lines.append(clean)
        if len(lines) == 2:
            break
    if not visible_files or not lines:
        return generic_changelog()
    return "\n".join(lines) + "\n"


def release_apk_input(repo: Path) -> Path:
    release_dir = repo / "app" / "build" / "outputs" / "apk" / "release"
    preferred = release_dir / "app-release-unsigned.apk"
    if preferred.exists():
        return preferred
    candidates = sorted(release_dir.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not candidates:
        raise ReleaseError(f"No release APK found in {release_dir}.")
    return candidates[0]


def collect_changelog(
    repo: Path,
    lines: list[str],
    previous_branch: str | None,
    target_branch: str,
    rollback: bool = False,
    hidden_terms: list[str] | None = None,
) -> str:
    clean = [line.strip() for line in lines if line.strip()]
    if len(clean) > 2:
        raise ReleaseError("Provide at most two changelog lines.")
    if clean:
        clean_text = "\n".join(clean)
        extra_hidden = [term for term in (hidden_terms or []) if term and term in clean_text]
        if contains_hidden_release_detail(clean_text) or (rollback and extra_hidden):
            raise ReleaseError(
                "Release changelog must not mention hidden, debug, rollback, downgrade, "
                "or internal release details. Omit --changelog to derive or use generic notes."
            )
        return "\n".join(clean) + "\n"

    if rollback:
        changelog = generic_changelog()
        print("Using generic changelog for rollback publish:")
        for line in changelog.strip().splitlines():
            print(f"  - {line}")
        return changelog

    changelog = derive_changelog_from_release_diff(repo, previous_branch, target_branch)
    print("Derived changelog:")
    for line in changelog.strip().splitlines():
        print(f"  - {line}")
    return changelog


def build_and_sign(repo: Path, config: dict[str, Any], version_name: str) -> Path:
    if os.name == "nt":
        gradlew = repo / "gradlew.bat"
    else:
        gradlew = repo / "gradlew"
    run_cmd([str(gradlew), ":app:assembleRelease"], repo)

    zipalign = find_build_tool(repo, "zipalign")
    apksigner = find_build_tool(repo, "apksigner")
    input_apk = release_apk_input(repo)
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    aligned = WORK_DIR / f"ahutong-release-{version_name}-aligned.apk"
    signed = WORK_DIR / f"ahutong-release-{version_name}.apk"

    run_cmd([str(zipalign), "-f", "-p", "4", str(input_apk), str(aligned)], repo)

    keystore = config["keystore"]
    sign_env = os.environ.copy()
    sign_env["AHUTONG_RELEASE_KS_PASSWORD"] = str(keystore["store_password"])
    sign_env["AHUTONG_RELEASE_KEY_PASSWORD"] = str(keystore["key_password"])
    sign_cmd = [
        str(apksigner),
        "sign",
        "--ks",
        str(keystore["path"]),
        "--ks-type",
        "PKCS12",
        "--ks-key-alias",
        str(keystore["key_alias"]),
        "--ks-pass",
        "env:AHUTONG_RELEASE_KS_PASSWORD",
        "--key-pass",
        "env:AHUTONG_RELEASE_KEY_PASSWORD",
        "--out",
        str(signed),
        str(aligned),
    ]
    run_cmd(
        sign_cmd,
        repo,
        redacted=f"{apksigner} sign --ks <keystore> --ks-key-alias <alias> --ks-pass <redacted> --key-pass <redacted> --out {signed} {aligned}",
        env=sign_env,
    )
    run_cmd([str(apksigner), "verify", "--verbose", "--print-certs", str(signed)], repo)
    return signed


def sanitize_backup_part(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", value)


def remote_write_text(sftp: Any, path: str, content: str) -> None:
    temp = f"{path}.uploading"
    with sftp.open(temp, "w") as handle:
        handle.write(content)
    sftp.rename(temp, path)


def upload_release(
    client: Any,
    apk_path: Path,
    target_code: int,
    target_name: str,
    backup_version: str,
    changelog: str,
) -> None:
    sftp = client.open_sftp()
    temp_apk = f"{REMOTE_APK}.uploading"
    print(f"[UPLOAD] {apk_path} -> {temp_apk}")
    sftp.put(str(apk_path), temp_apk)
    backup_suffix = sanitize_backup_part(backup_version)
    script = f"""
set -e
final={shlex.quote(REMOTE_APK)}
temp={shlex.quote(temp_apk)}
backup={shlex.quote(REMOTE_APK + '_' + backup_suffix + '.bak')}
if [ -f "$final" ]; then
  if [ -e "$backup" ]; then
    backup="${{backup}}_$(date +%Y%m%d%H%M%S)"
  fi
  mv "$final" "$backup"
fi
mv "$temp" "$final"
chmod 644 "$final"
"""
    ssh_run(client, script)
    remote_write_text(sftp, REMOTE_APK_VERSION, f"{target_code}\n")
    remote_write_text(sftp, REMOTE_APK_VERSION_NAME, f"{target_name}\n")
    remote_write_text(sftp, REMOTE_CHANGELOG, changelog)
    sftp.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build, sign, and publish AHUTong release APK.")
    parser.add_argument("--repo-root", type=Path, default=None)
    parser.add_argument("--config", type=Path, default=None)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true")
    mode.add_argument("--setup-config", action="store_true")
    mode.add_argument("--print-config-path", action="store_true")
    parser.add_argument("--version-code", type=int, default=None)
    parser.add_argument("--version-name", default=None)
    parser.add_argument("--changelog", action="append", default=[])
    parser.add_argument("--previous-release-ref", default="HEAD~1")
    parser.add_argument("--base-branch", default="master")
    parser.add_argument("--on-version-mismatch", choices=["ask", "upgrade", "abort"], default="ask")
    mode.add_argument("--print-config-fields", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.print_config_fields:
        print(CONFIG_FIELD_HELP)
        return 0

    repo = args.repo_root.resolve() if args.repo_root else repo_root_from(Path.cwd())
    config_path = args.config.expanduser().resolve() if args.config else default_config_path(repo)
    if args.print_config_path:
        print(config_path)
        return 0
    if args.setup_config:
        load_or_create_config(config_path, dry_run=False, allow_create=True)
        print(f"Release config is ready: {config_path}")
        return 0
    config = load_or_create_config(config_path, args.dry_run)

    client = ssh_connect(config)
    try:
        local_code, local_name = parse_local_versions(repo)
        server_code_text = remote_read_text(client, REMOTE_APK_VERSION, required=True)
        server_name = remote_read_text(client, REMOTE_APK_VERSION_NAME, required=False)
        server_code = int(server_code_text or "0")
        version_plan = resolve_versions(
            local_code=local_code,
            local_name=local_name,
            server_code=server_code,
            server_name=server_name,
            explicit_code=args.version_code,
            explicit_name=args.version_name,
            on_mismatch=args.on_version_mismatch,
        )
        target_code = version_plan.code
        target_name = version_plan.name
        previous_release_name = (
            None
            if version_plan.rollback or version_plan.same_version_hotfix
            else (server_name or local_name)
        )
        previous_branch = (
            release_branch(previous_release_name)
            if previous_release_name and previous_release_name != target_name
            else None
        )
        target_branch = release_branch(target_name)
        release_commit = None
        if args.dry_run:
            fetch_origin(repo)
            if version_plan.same_version_hotfix:
                fetch_origin_branches(repo, [args.base_branch, target_branch])
            if version_plan.rollback and not release_branch_available(repo, target_name):
                raise ReleaseError(
                    f"Rollback publish requires existing {target_branch} locally or on origin; "
                    "refusing to create it from the current branch."
                )
            if version_plan.same_version_hotfix:
                source_branch, _, release_ref, source_commits = same_version_hotfix_source_commits(
                    repo,
                    target_name,
                    args.base_branch,
                )
                print(
                    f"[DRY-RUN] Would apply {len(source_commits)} commit(s) from "
                    f"{source_branch} to {release_ref}."
                )
                print(
                    f"[DRY-RUN] Would commit versionCode={target_code}, "
                    f"versionName={target_name} and atomically push "
                    f"{args.base_branch} with {target_branch}."
                )
            elif not version_plan.rollback:
                source_branch = git_capture(repo, ["branch", "--show-current"])
                print(f"[DRY-RUN] Would fast-forward merge {source_branch} into {args.base_branch}.")
                print(
                    f"[DRY-RUN] Would update app/build.gradle.kts on {args.base_branch}, "
                    f"commit versionCode={target_code}, versionName={target_name}, and push {args.base_branch}."
                )
                print(f"[DRY-RUN] Would create or validate {target_branch} at current {args.base_branch}.")
            print(f"[DRY-RUN] Would ensure previous release branch: {previous_branch or '(none)'}")
            print(f"[DRY-RUN] Would switch to or create target release branch: {target_branch}")
        else:
            previous_branch, target_branch = prepare_release_branches(
                repo=repo,
                previous_version=previous_release_name,
                target_version=target_name,
                target_code=target_code,
                previous_release_ref=args.previous_release_ref,
                rollback=version_plan.rollback,
                same_version_hotfix=version_plan.same_version_hotfix,
                base_branch=args.base_branch,
            )
            release_commit = require_pushed_release_branch(repo, target_branch)

        changelog = collect_changelog(
            repo,
            args.changelog,
            previous_branch,
            target_branch,
            rollback=version_plan.rollback,
            hidden_terms=[target_name, server_name or ""],
        )
        print(f"Target release: versionCode={target_code}, versionName={target_name}")
        print(f"Release branch: {target_branch}")
        if release_commit:
            print(f"Release commit: {release_commit}")
        if version_plan.rollback:
            print("Release mode: rollback publish with incremented versionCode")
        elif version_plan.same_version_hotfix:
            print("Release mode: same-version hotfix with incremented versionCode")
        print(f"Android SDK: {android_sdk_dir(repo)}")
        print(f"zipalign: {find_build_tool(repo, 'zipalign')}")
        print(f"apksigner: {find_build_tool(repo, 'apksigner')}")

        if args.dry_run:
            if version_plan.rollback:
                print("[DRY-RUN] Would update app/build.gradle.kts on the rollback release branch.")
            print("[DRY-RUN] Would build, zipalign, sign, verify, and upload release APK.")
            print("[DRY-RUN] Would update apk_version.txt, apk_version_name.txt, and apk_changelog.txt.")
            return 0

        backup_version = server_name or str(server_code)
        if version_plan.rollback:
            with temporary_local_versions(repo, target_code, target_name):
                signed_apk = build_and_sign(repo, config, target_name)
                upload_release(client, signed_apk, target_code, target_name, backup_version, changelog)
        else:
            signed_apk = build_and_sign(repo, config, target_name)
            upload_release(client, signed_apk, target_code, target_name, backup_version, changelog)
        print("Release publish completed.")
        print(f"Immutable release target: {target_branch}@{release_commit}")
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ReleaseError as exc:
        fail(str(exc))
