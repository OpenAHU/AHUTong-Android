"""AHUTong CLI: typed business commands and explicit Android UI delegation.

Uses only Python's standard library and authorized ADB. No credentials are stored
on the host. Production APKs do not expose the debug companion provider.
"""
import argparse
import base64
import getpass
import hashlib
import json
import math
import os
import re
import shlex
import subprocess
import tempfile
import shutil
import sys
from pathlib import Path


class CliError(Exception):
    def __init__(self, code, message, exit_code=1):
        super().__init__(message)
        self.code, self.exit_code = code, exit_code


ALIASES = {
    "schedule": "get_schedule", "grades": "get_grades", "exams": "get_exams",
    "balance": "get_balance", "bills": "get_bills", "electricity": "get_electricity",
    "assignments": "get_assignments", "weather": "get_weather", "notices": "get_notices",
    "phonebook": "get_phone_book", "rooms": "get_free_rooms", "attendance": "get_attendance",
    "program": "get_program_completion", "canteen": "get_canteen_ranking",
    "lost-found": "get_lost_found", "evaluation": "get_evaluation_tasks",
    "wall-search": "search_campus_wall", "wall-topic": "read_campus_topic",
}


class AndroidTransport:
    def __init__(self, adb="adb", serial=None, package="com.ahu.ahutong.debug", timeout=135, user=0):
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+", package):
            raise CliError("invalid_package", "Invalid Android package name", 2)
        self.adb, self.serial, self.package = adb, serial, package
        self.timeout, self.user = timeout, user

    def _run(self, argv, **kwargs):
        try:
            return subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                  encoding="utf-8", errors="replace", timeout=self.timeout, **kwargs)
        except FileNotFoundError:
            raise CliError("adb_missing", "Install Android platform-tools or select --adb", 3)
        except subprocess.TimeoutExpired:
            raise CliError("timeout", "Request timed out; verify the result before retrying any write", 3)

    def device(self):
        if self.serial:
            return self.serial
        reply = self._run([self.adb, "devices"])
        if reply.returncode:
            raise CliError("adb_failed", "ADB device discovery failed", 3)
        devices = [line.split()[0] for line in reply.stdout.splitlines()
                   if len(line.split()) == 2 and line.split()[1] == "device"]
        if len(devices) != 1:
            raise CliError("device_required", "Connect one authorized device or specify --serial", 3)
        self.serial = devices[0]
        return self.serial

    def request(self, command, arguments=None, **extra):
        request = dict(version=1, command=command, arguments=arguments or {}, **extra)
        raw = json.dumps(request, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        encoded = base64.b64encode(raw.encode()).decode("ascii")
        if len(encoded) > 96000:
            raise CliError("request_too_large", "Use a smaller request or pagination", 2)
        # Send the shell command through stdin, not host argv; no shell=True and no logs.
        shell = ["content", "call", "--user", str(self.user), "--uri",
                 "content://" + self.package + ".cli", "--method", "request", "--arg", encoded]
        reply = self._run([self.adb, "-s", self.device(), "shell"], input=shlex.join(shell) + "\n")
        match = re.search(r"\bresponse=([A-Za-z0-9+/]+={0,2})", reply.stdout)
        if reply.returncode or not match:
            raise CliError("companion_unavailable",
                           "Cannot reach companion. Install this branch's Debug APK for the selected user; "
                           "release APKs do not expose CLI access", 3)
        try:
            result = json.loads(base64.b64decode(match.group(1), validate=True).decode("utf-8"))
            if not isinstance(result, dict) or not isinstance(result.get("ok"), bool):
                raise ValueError()
        except (ValueError, UnicodeError):
            raise CliError("invalid_response", "Invalid companion protocol response", 3)
        if not result["ok"]:
            raise CliError(result.get("code", "request_failed"), result.get("message", "Request failed"))
        return result.get("data")

    def _file_uri(self, info):
        uri = info.get("uri", "")
        pattern = r"content://" + re.escape(self.package) + r"\.cli/file/[a-f0-9-]{36}"
        if not re.fullmatch(pattern, uri):
            raise CliError("invalid_response", "Invalid companion file URI", 3)
        return uri

    def launch(self):
        reply = self._run([self.adb, "-s", self.device(), "shell", "am", "start", "-W", "--user", str(self.user),
                           "-n", self.package + "/com.ahu.ahutong.MainActivity"])
        if reply.returncode or "Error:" in reply.stdout:
            raise CliError("launch_failed", "Android could not launch the companion activity", 3)

    def _binary(self, verb, info, **streams):
        command = [self.adb, "-s", self.device(), "exec-in" if verb == "write" else "exec-out",
                   "content", verb, "--user", str(self.user), "--uri", self._file_uri(info)]
        try:
            reply = subprocess.run(command, stderr=subprocess.PIPE, timeout=self.timeout, **streams)
        except subprocess.TimeoutExpired:
            raise CliError("transfer_timeout", "File transfer timed out", 3)
        if reply.returncode:
            raise CliError("transfer_failed", "ADB file transfer failed", 3)

    @staticmethod
    def digest(path):
        digest = hashlib.sha256()
        with Path(path).open("rb") as stream:
            for chunk in iter(lambda: stream.read(65536), b""):
                digest.update(chunk)
        return digest.hexdigest()

    def download(self, info, output, force=False):
        output = Path(output)
        if output.exists() and not force:
            raise CliError("output_exists", "Output exists; use --force to replace it", 2)
        if not re.fullmatch(r"[a-f0-9]{64}", info.get("sha256", "")) or type(info.get("bytes")) is not int:
            raise CliError("invalid_response", "Missing file integrity metadata", 3)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=output.parent, prefix=".ahu-", delete=False) as stream:
                temporary = Path(stream.name)
                self._binary("read", info, stdout=stream)
            if temporary.stat().st_size != info["bytes"] or self.digest(temporary) != info["sha256"]:
                raise CliError("digest_mismatch", "File size or SHA-256 did not match; output was not replaced", 3)
            if force:
                os.replace(str(temporary), str(output))
            else:
                # Exclusive creation also protects against another process creating the destination meanwhile.
                with output.open("xb") as target, temporary.open("rb") as source:
                    shutil.copyfileobj(source, target)
            return {"path": str(output.resolve()), "bytes": info["bytes"], "sha256": info["sha256"]}
        finally:
            if temporary is not None and temporary.exists():
                temporary.unlink()

    def upload(self, info, source):
        source = Path(source)
        if not 0 < source.stat().st_size <= 50 * 1024 * 1024:
            raise CliError("invalid_file", "Uploads must contain 1 byte through 50 MiB", 2)
        expected = self.digest(source)
        with source.open("rb") as stream:
            self._binary("write", info, stdin=stream, stdout=subprocess.PIPE)
        uploaded = self.request("file-info", {"id": info["id"]})["result"]
        if uploaded["sha256"] != expected or uploaded["bytes"] != source.stat().st_size:
            raise CliError("digest_mismatch", "Uploaded file changed during transfer", 3)
        return uploaded


def arguments_for(schema, values, tokens):
    """Decode command flags from the companion's actual JSON Schema, rejecting typos."""
    properties = schema.get("properties", {})
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if not token.startswith("--"):
            raise CliError("invalid_arguments", "Expected --name value: " + token, 2)
        name, separator, raw = token[2:].partition("=")
        if name not in properties:
            raise CliError("invalid_arguments", "Unknown argument: " + name, 2)
        kind = properties[name].get("type")
        if not separator:
            if index + 1 < len(tokens) and not tokens[index + 1].startswith("--"):
                index += 1
                raw = tokens[index]
            elif kind == "boolean":
                raw = "true"
            else:
                raise CliError("invalid_arguments", "Missing value for " + name, 2)
        if name in values:
            raise CliError("invalid_arguments", "Duplicate argument: " + name, 2)
        if kind == "string":
            value = raw
        else:
            try:
                value = json.loads(raw, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
            except ValueError:
                raise CliError("invalid_arguments", "Expected a JSON " + str(kind) + " for " + name, 2)
        values[name] = value
        index += 1
    validate(schema, values)
    return values


def validate(schema, value, path="arguments"):
    if "enum" in schema and value not in schema["enum"]:
        raise CliError("invalid_arguments", path + " is not an allowed value", 2)
    kind = schema.get("type")
    expected = {"object": lambda v: isinstance(v, dict), "array": lambda v: isinstance(v, list),
                "string": lambda v: isinstance(v, str), "boolean": lambda v: isinstance(v, bool),
                "integer": lambda v: type(v) is int,
                "number": lambda v: type(v) in (int, float) and math.isfinite(v)}
    if kind in expected and not expected[kind](value):
        raise CliError("invalid_arguments", path + " must be " + kind, 2)
    if kind == "object":
        properties = schema.get("properties", {})
        missing = set(schema.get("required", [])) - set(value)
        if missing:
            raise CliError("invalid_arguments", "Missing: " + ", ".join(sorted(missing)), 2)
        if schema.get("additionalProperties") is False and set(value) - set(properties):
            raise CliError("invalid_arguments", "Unknown argument keys", 2)
        for name, entry in value.items():
            if name in properties:
                validate(properties[name], entry, path + "." + name)
    elif kind == "array" and "items" in schema:
        for item in value:
            validate(schema["items"], item, path + "[]")
    elif kind in ("integer", "number"):
        if "minimum" in schema and value < schema["minimum"] or "maximum" in schema and value > schema["maximum"]:
            raise CliError("invalid_arguments", path + " is outside the allowed range", 2)


def terminal_text(value):
    # Mail, notices and model text are untrusted terminal input (including OSC/CSI).
    return "".join(char for char in str(value) if char in "\n\t" or
                   ord(char) >= 32 and not 127 <= ord(char) < 160)


def render(value, as_json=False):
    if as_json:
        print(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False))
        return
    if isinstance(value, dict) and value.get("appDemoMode"):
        print("[App demo mode: campus results may contain demo data]")
    if isinstance(value, dict) and "result" in value:
        result = value["result"]
        if isinstance(result, dict) and "summary" in result:
            print(terminal_text(result["summary"]))
            for line in result.get("lines", []):
                print(terminal_text(line))
            if result.get("route"):
                print("Android page: " + terminal_text(result["route"]))
            return
    print(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False))


def parser():
    p = argparse.ArgumentParser(prog="ahu", add_help=False, allow_abbrev=False,
        description="AHUTong CLI — business commands through the authorized Android debug companion")
    p.add_argument("command", nargs="?", default="help")
    p.add_argument("--help", "-h", action="store_true")
    p.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    p.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    p.add_argument("--package", default="com.ahu.ahutong.debug")
    p.add_argument("--user", type=int, default=0)
    p.add_argument("--timeout", type=int, default=135)
    p.add_argument("--json", action="store_true")
    p.add_argument("--yes", action="store_true", help="Explicitly confirm a write after showing its preview")
    p.add_argument("--args", help="JSON argument object; use --args-file for long inputs")
    p.add_argument("--args-file", type=Path)
    p.add_argument("--secret-env", help="Read a password/Key from this environment variable, never from arguments")
    p.add_argument("--input", type=Path, help="File to stage with file-upload")
    p.add_argument("--output", type=Path, help="Host destination for an exported file")
    p.add_argument("--force", action="store_true", help="Explicitly replace an existing host output file")
    return p


def run(argv=None, transport_factory=AndroidTransport):
    p = parser()
    opts, tokens = p.parse_known_args(argv)
    if opts.command == "help" or opts.help and opts.command == "help":
        p.print_help()
        print("\nCommands: status, catalog, pages, features, open ROUTE, ask --question TEXT,")
        print("          call TOOL, schedule, grades, exams, balance, bills, electricity,")
        print("          resources, mail-folders, mail-messages, notes-subjects, plugins, ...")
        print("Use 'ahu catalog' for all business commands and 'ahu COMMAND --help' for its schema.")
        print("UI-only functionality is explicitly marked android-ui; there is no standalone full-parity mode.")
        return 0
    if opts.timeout < 1 or opts.user < 0:
        raise CliError("invalid_arguments", "Invalid timeout or Android user", 2)
    transport = transport_factory(opts.adb, opts.serial, opts.package, opts.timeout, opts.user)
    catalog = transport.request("catalog")
    if opts.command in ("catalog", "pages", "features"):
        if tokens:
            raise CliError("invalid_arguments", "Unexpected catalog arguments", 2)
        value = catalog if opts.command == "catalog" else catalog["pages"] if opts.command == "pages" else {
            "widgets": catalog["widgets"], "pages": catalog["pages"],
            "commands": [{"name": item["name"], "mode": item["mode"]} for item in catalog["commands"]],
            "limitations": catalog["limitations"],
        }
        render(value, opts.json)
        return 0
    name = opts.command
    if name == "call":
        if not tokens or tokens[0].startswith("--"):
            raise CliError("invalid_arguments", "Use ahu call COMMAND", 2)
        name, tokens = tokens[0], tokens[1:]
    name = ALIASES.get(name, name)
    if opts.input and (name != "file-upload" or opts.output):
        raise CliError("invalid_arguments", "--input is only valid for file-upload", 2)
    if opts.output and opts.output.exists() and not opts.force:
        raise CliError("output_exists", "Output exists; use --force to replace it", 2)
    commands = {entry["name"]: entry for entry in catalog["commands"]}
    if name not in commands:
        raise CliError("unknown_command", "Unknown command; use ahu catalog", 2)
    spec = commands[name]
    if opts.help:
        render(spec, True)
        return 0
    if opts.args and opts.args_file:
        raise CliError("invalid_arguments", "Choose either --args or --args-file", 2)
    try:
        values = json.loads(opts.args_file.read_text(encoding="utf-8-sig") if opts.args_file else opts.args or "{}",
                            parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except (ValueError, OSError):
        raise CliError("invalid_arguments", "Cannot read the JSON argument object", 2)
    if not isinstance(values, dict):
        raise CliError("invalid_arguments", "Arguments must be a JSON object", 2)
    if name == "open" and tokens and not tokens[0].startswith("--"):
        values["route"], tokens = tokens[0], tokens[1:]
    values = arguments_for(spec["schema"], values, tokens)
    extra = {}
    if spec.get("write"):
        preview = transport.request(name, values, phase="prepare")
        # A companion cannot silently replace the action that was reviewed on the host.
        if preview.get("command") != name or preview.get("arguments") != values:
            raise CliError("preview_mismatch", "Companion preview did not match the request", 3)
        print(json.dumps({key: entry for key, entry in preview.items() if key != "confirmation"}, ensure_ascii=False, indent=2), file=sys.stderr)
        if not opts.yes:
            if not sys.stdin.isatty() or input("Execute this write? Type yes: ").strip() != "yes":
                raise CliError("cancelled", "Write cancelled; no execution request sent", 2)
        extra["confirmation"] = preview["confirmation"]
    if spec.get("secret"):
        if opts.secret_env:
            secret = os.environ.get(opts.secret_env)
            if not secret:
                raise CliError("secret_required", "The selected secret environment variable is empty", 2)
        else:
            if not sys.stdin.isatty():
                raise CliError("secret_required", "Use an interactive terminal or --secret-env NAME", 2)
            secret = getpass.getpass("Password / API Key (not stored on host): ")
        extra["secret"] = secret
    result = transport.request(name, values, **extra)
    if name == "open":
        transport.launch()
    if opts.input:
        if not isinstance(result, dict) or not isinstance(result.get("result"), dict):
            raise CliError("invalid_response", "Expected upload metadata", 3)
        result = transport.upload(result["result"], opts.input)
    elif opts.output:
        if not isinstance(result, dict) or not isinstance(result.get("result"), dict):
            raise CliError("invalid_response", "Expected exported file metadata", 3)
        result = transport.download(result["result"], opts.output, opts.force)
    render(result, opts.json)
    return 0


def main():
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    try:
        return run()
    except CliError as error:
        print(json.dumps({"ok": False, "code": error.code, "message": str(error)}, ensure_ascii=False), file=sys.stderr)
        return error.exit_code
    except (KeyboardInterrupt, EOFError):
        print("Cancelled. If a write was already sent, verify its result before retrying.", file=sys.stderr)
        return 130
    except OSError:
        print("Host file operation failed; existing outputs are preserved unless --force was selected.", file=sys.stderr)
        return 3


if __name__ == "__main__":
    sys.exit(main())
