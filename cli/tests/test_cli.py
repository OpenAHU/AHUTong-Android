import base64
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import hashlib
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ahutong_cli as cli


SCHEMA = {"type": "object", "properties": {"amount": {"type": "number", "minimum": 0.01, "maximum": 2000}},
          "required": ["amount"], "additionalProperties": False}


class FakeTransport:
    requests = []
    def __init__(self, *args):
        pass

    def request(self, name, args=None, **extra):
        self.requests.append((name, args, extra))
        if name == "catalog":
            return {"commands": [{"name": "pay", "schema": SCHEMA, "write": True, "secret": True},
                                 {"name": "open", "schema": {"type": "object", "properties": {"route": {"type": "string"}}, "required": ["route"]}, "write": False},
                                 {"name": "read", "schema": {"type": "object", "properties": {}}, "write": False}]}
        if extra.get("phase") == "prepare":
            return {"command": name, "arguments": args, "confirmation": "one-shot", "requiresSecret": True}
        return {"mode": "business", "result": "complete"}

    def launch(self):
        self.requests.append(("launch", None, {}))


class CliTest(unittest.TestCase):
    def setUp(self):
        FakeTransport.requests = []

    def test_noninteractive_write_is_not_executed_without_confirmation(self):
        with patch("sys.stdin.isatty", return_value=False), contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(cli.CliError) as error:
                cli.run(["pay", "--amount", "10"], FakeTransport)
        self.assertEqual(error.exception.code, "cancelled")
        self.assertEqual([x[2].get("phase") for x in FakeTransport.requests], [None, "prepare"])

    def test_confirmed_write_uses_secret_and_bound_ticket(self):
        with patch.dict(os.environ, {"TEST_AHU_SECRET": "synthetic-secret"}), \
             contextlib.redirect_stdout(io.StringIO()) as out, contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(cli.run(["pay", "--amount", "10", "--yes", "--secret-env", "TEST_AHU_SECRET"], FakeTransport), 0)
        self.assertEqual(FakeTransport.requests[-1], ("pay", {"amount": 10}, {"confirmation": "one-shot", "secret": "synthetic-secret"}))
        self.assertNotIn("synthetic-secret", out.getvalue() + err.getvalue())
        self.assertNotIn("one-shot", err.getvalue())

    def test_invalid_amount_is_rejected_before_prepare(self):
        for value in ("NaN", "Infinity", "true", "0", "2001", '"12"'):
            with self.subTest(value=value), self.assertRaises(cli.CliError):
                cli.run(["pay", "--amount", value], FakeTransport)
        self.assertTrue(all(x[0] == "catalog" for x in FakeTransport.requests))

    def test_changed_preview_is_never_executed(self):
        class Changed(FakeTransport):
            def request(self, name, args=None, **extra):
                result = super().request(name, args, **extra)
                if extra.get("phase") == "prepare":
                    result["arguments"] = {"amount": 999}
                return result
        with self.assertRaises(cli.CliError) as error:
            cli.run(["pay", "--amount", "10", "--yes"], Changed)
        self.assertEqual(error.exception.code, "preview_mismatch")
        self.assertEqual(len(FakeTransport.requests), 2)

    def test_argument_types_duplicates_and_unknown_fields(self):
        for tokens in (["--amunt", "10"], ["--amount", "10", "--amount", "11"]):
            with self.assertRaises(cli.CliError):
                cli.arguments_for(SCHEMA, {}, tokens)
        with self.assertRaises(cli.CliError):
            cli.validate(SCHEMA, {"amount": 10, "password": "secret"})

    def test_read_needs_no_confirmation(self):
        with contextlib.redirect_stdout(io.StringIO()):
            cli.run(["read", "--json"], FakeTransport)
        self.assertEqual(FakeTransport.requests[-1], ("read", {}, {}))

    def test_demo_data_is_marked_in_human_and_json_output(self):
        data = {"appDemoMode": True, "result": {"summary": "Synthetic balance"}}
        with contextlib.redirect_stdout(io.StringIO()) as human:
            cli.render(data)
        self.assertIn("demo mode", human.getvalue())
        with contextlib.redirect_stdout(io.StringIO()) as machine:
            cli.render(data, True)
        self.assertTrue(json.loads(machine.getvalue())["appDemoMode"])

    def test_external_text_cannot_emit_terminal_control_sequences(self):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            cli.render({"result": {"summary": "\x1b]52;c;payload\x07\rsafe\b", "lines": ["\x9b31mline"]}})
        for control in ("\x1b", "\x07", "\r", "\b", "\x9b"):
            self.assertNotIn(control, output.getvalue())

    def test_open_publishes_route_before_shell_foreground_launch(self):
        with contextlib.redirect_stdout(io.StringIO()):
            cli.run(["open", "camera"], FakeTransport)
        self.assertEqual(FakeTransport.requests[-2:], [("open", {"route": "camera"}, {}), ("launch", None, {})])

    def test_json_arguments_reject_nonstandard_numbers_before_prepare(self):
        with self.assertRaises(cli.CliError):
            cli.run(["pay", "--args", '{"amount":NaN}', "--yes"], FakeTransport)
        self.assertEqual(len(FakeTransport.requests), 1)

    def test_multiple_devices_require_selection(self):
        transport = cli.AndroidTransport()
        transport._run = lambda *a, **k: subprocess.CompletedProcess([], 0, "List of devices attached\na\tdevice\nb\tdevice\n", "")
        with self.assertRaises(cli.CliError) as error:
            transport.device()
        self.assertEqual(error.exception.code, "device_required")

    def test_transport_encodes_input_and_never_uses_shell_true(self):
        response = base64.b64encode(json.dumps({"ok": True, "data": {"safe": True}}).encode()).decode()
        seen = []
        def run(argv, **kwargs):
            seen.append((argv, kwargs))
            return subprocess.CompletedProcess(argv, 0, "Result: Bundle[{response=" + response + "}]", "")
        transport = cli.AndroidTransport(serial="selected")
        value = "'; $(do-not-run) \"\n内容"
        with patch("subprocess.run", side_effect=run):
            self.assertEqual(transport.request("read", {"text": value}), {"safe": True})
        argv, kwargs = seen[0]
        self.assertEqual(argv, ["adb", "-s", "selected", "shell"])
        self.assertNotIn("shell", kwargs)
        self.assertNotIn(value, str(argv) + kwargs["input"])
        encoded = kwargs["input"].strip().split()[-1]
        self.assertEqual(json.loads(base64.b64decode(encoded))["arguments"]["text"], value)

    def test_timeout_does_not_retry_a_write(self):
        with patch("subprocess.run", side_effect=subprocess.TimeoutExpired("adb", 1)) as run:
            with self.assertRaises(cli.CliError) as error:
                cli.AndroidTransport(serial="selected").request("pay")
        self.assertEqual(error.exception.code, "timeout")
        self.assertEqual(run.call_count, 1)

    def test_invalid_package_cannot_become_shell_input(self):
        with self.assertRaises(cli.CliError):
            cli.AndroidTransport(package="com.example;evil")

    def test_download_verifies_digest_before_replacing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "out.bin"
            output.write_bytes(b"existing")
            transport = cli.AndroidTransport(serial="selected")
            transport._binary = lambda verb, info, **streams: streams["stdout"].write(b"tampered")
            info = {"bytes": 8, "sha256": hashlib.sha256(b"expected").hexdigest()}
            with self.assertRaises(cli.CliError) as error:
                transport.download(info, output, force=True)
            self.assertEqual(error.exception.code, "digest_mismatch")
            self.assertEqual(output.read_bytes(), b"existing")
            self.assertEqual(list(Path(directory).iterdir()), [output])

    def test_download_refuses_existing_output_without_force(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "out.bin"
            output.write_bytes(b"existing")
            with self.assertRaises(cli.CliError):
                cli.AndroidTransport(serial="selected").download({}, output)
            self.assertEqual(output.read_bytes(), b"existing")

    def test_valid_download_reaches_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "out.bin"
            transport = cli.AndroidTransport(serial="selected")
            transport._binary = lambda verb, info, **streams: streams["stdout"].write(b"payload")
            info = {"bytes": 7, "sha256": hashlib.sha256(b"payload").hexdigest()}
            self.assertEqual(transport.download(info, output)["bytes"], 7)
            self.assertEqual(output.read_bytes(), b"payload")

    def test_provider_cannot_redirect_binary_transfer_to_arbitrary_uri(self):
        transport = cli.AndroidTransport(serial="selected")
        with self.assertRaises(cli.CliError):
            transport._file_uri({"uri": "content://other.provider/private"})


if __name__ == "__main__":
    unittest.main()
