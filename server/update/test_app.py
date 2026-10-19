import os
import hashlib
import sqlite3
import tempfile
import unittest
from contextlib import closing
from unittest.mock import patch

import app as update_app


class SchoolCalendarApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.static_dir = self.temp_dir.name
        self.calendar_dir = os.path.join(self.static_dir, "calendars")
        os.makedirs(self.calendar_dir)

        self.original_file_dir = update_app.FILE_DIR
        self.original_calendar_dir = update_app.CALENDAR_DIR
        update_app.FILE_DIR = self.static_dir
        update_app.CALENDAR_DIR = self.calendar_dir
        update_app.app.config.update(TESTING=True)
        self.client = update_app.app.test_client()

    def tearDown(self):
        update_app.FILE_DIR = self.original_file_dir
        update_app.CALENDAR_DIR = self.original_calendar_dir
        self.temp_dir.cleanup()

    def write_file(self, relative_path, content=b"jpeg"):
        path = os.path.join(self.static_dir, relative_path)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as file:
            file.write(content)

    def test_empty_calendar_directory_returns_empty_list(self):
        response = self.client.get("/api/school_calendars")

        self.assertEqual(200, response.status_code)
        self.assertEqual({"latestYear": None, "years": []}, response.get_json())

    def test_list_contains_only_existing_valid_academic_years(self):
        self.write_file("calendars/2024-2025.jpg")
        self.write_file("calendars/2026-2027.jpg")
        self.write_file("calendars/2026-2028.jpg")
        self.write_file("calendars/not-a-year.jpg")
        self.write_file("calendars/2025-2026.png")

        response = self.client.get("/api/school_calendars")

        self.assertEqual(200, response.status_code)
        self.assertEqual(
            {
                "latestYear": "2026-2027",
                "years": ["2026-2027", "2024-2025"],
            },
            response.get_json(),
        )

    def test_calendar_image_is_returned_for_valid_existing_year(self):
        self.write_file("calendars/2025-2026.jpg", b"calendar-image")

        response = self.client.get("/api/school_calendars/2025-2026")

        self.assertEqual(200, response.status_code)
        self.assertEqual("image/jpeg", response.content_type)
        self.assertEqual(b"calendar-image", response.data)
        response.close()

    def test_invalid_or_missing_calendar_returns_not_found(self):
        self.assertEqual(
            403,
            self.client.get("/api/school_calendars/../../etc/passwd").status_code,
        )
        self.assertEqual(
            404,
            self.client.get("/api/school_calendars/2025-2027").status_code,
        )
        self.assertEqual(
            404,
            self.client.get("/api/school_calendars/2025-2026").status_code,
        )

    def test_legacy_calendar_download_is_unchanged(self):
        self.write_file("xiaoli.jpg", b"legacy-calendar")

        response = self.client.get("/download/xiaoli.jpg")

        self.assertEqual(200, response.status_code)
        self.assertEqual(b"legacy-calendar", response.data)
        response.close()


class EarlyAccessApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.static_dir = os.path.join(self.temp_dir.name, "static")
        os.makedirs(self.static_dir)
        self.db_path = os.path.join(self.temp_dir.name, "early_access.sqlite3")
        self.subject = hashlib.sha256(b"user:test-account").hexdigest()
        self.other_subject = hashlib.sha256(b"user:other-account").hexdigest()

        file_dir_patch = patch.object(update_app, "FILE_DIR", self.static_dir)
        file_dir_patch.start()
        self.addCleanup(file_dir_patch.stop)
        config_patch = patch.dict(update_app.app.config, TESTING=True, DB_PATH=self.db_path)
        config_patch.start()
        self.addCleanup(config_patch.stop)
        signature_patch = patch.object(
            update_app, "sign_file_sha256_digest", return_value="test-signature"
        )
        self.sign_file = signature_patch.start()
        self.addCleanup(signature_patch.stop)
        self.client = update_app.app.test_client()

    def membership(self, method="GET", subject=None):
        subject = self.subject if subject is None else subject
        if method == "GET":
            return self.client.get("/api/early-access", query_string={"subject": subject})
        return self.client.open(
            "/api/early-access", method=method, json={"subject": subject}
        )

    def write_package(self, channel, version, optional_metadata=True):
        directory = self.static_dir
        if channel == "preview":
            directory = os.path.join(directory, "preview")
        os.makedirs(directory, exist_ok=True)
        apk_path = os.path.join(directory, update_app.APK_FILENAME)
        with open(apk_path, "wb") as file:
            file.write(channel.encode("utf-8"))
        metadata = {update_app.APK_VERSION_FILENAME: str(version)}
        if optional_metadata:
            metadata.update({
                update_app.APK_VERSION_NAME_FILENAME: f"{channel}-{version}",
                update_app.APK_CHANGELOG_FILENAME: f"{channel} changelog",
                update_app.APK_FORCE_FILENAME: "true" if channel == "preview" else "false",
            })
        for filename, content in metadata.items():
            with open(os.path.join(directory, filename), "w", encoding="utf-8") as file:
                file.write(content)
        return apk_path

    def check_update(self, subject=None):
        query = {} if subject is None else {"subject": subject}
        return self.client.get("/api/check_apk_update", query_string=query)

    def test_membership_defaults_to_stable_and_join_is_idempotent(self):
        self.assertEqual({"joined": False}, self.membership().get_json())
        for _ in range(2):
            response = self.membership("POST")
            self.assertEqual(200, response.status_code)
            self.assertEqual({"joined": True}, response.get_json())
        self.assertEqual({"joined": True}, self.membership().get_json())
        self.assertEqual({"joined": False}, self.membership(subject=self.other_subject).get_json())
        with closing(sqlite3.connect(self.db_path)) as database:
            self.assertEqual(
                [(self.subject,)],
                database.execute("SELECT * FROM early_access_members").fetchall(),
            )

    def test_leave_is_idempotent_and_removes_membership(self):
        self.assertEqual({"joined": False}, self.membership("DELETE").get_json())
        self.membership("POST")
        for _ in range(2):
            response = self.membership("DELETE")
            self.assertEqual(200, response.status_code)
            self.assertEqual({"joined": False}, response.get_json())
        self.assertEqual({"joined": False}, self.membership().get_json())
        with closing(sqlite3.connect(self.db_path)) as database:
            self.assertEqual(0, database.execute("SELECT COUNT(*) FROM early_access_members").fetchone()[0])

    def test_membership_rejects_invalid_subjects_without_creating_database(self):
        invalid_subjects = [
            "", "student-number", "a" * 63, "a" * 65, "g" * 64,
            self.subject.upper(), f" {self.subject}", 123, None, [], {},
        ]
        for subject in invalid_subjects:
            for method in ("GET", "POST", "DELETE"):
                with self.subTest(method=method, subject=subject):
                    if method == "GET":
                        response = self.client.get(
                            "/api/early-access", query_string={"subject": subject}
                        )
                    else:
                        response = self.client.open(
                            "/api/early-access", method=method, json={"subject": subject}
                        )
                    self.assertEqual(400, response.status_code)
        self.assertFalse(os.path.exists(self.db_path))

    def test_membership_rejects_missing_duplicate_or_extra_fields(self):
        self.assertEqual(400, self.client.get("/api/early-access").status_code)
        self.assertEqual(400, self.client.get(
            "/api/early-access", query_string={"subject": self.subject, "extra": "value"}
        ).status_code)
        self.assertEqual(400, self.client.get(
            "/api/early-access", query_string=[("subject", self.subject), ("subject", self.subject)]
        ).status_code)
        for method in ("POST", "DELETE"):
            for body in ({}, [], {"subject": self.subject, "extra": True}):
                with self.subTest(method=method, body=body):
                    self.assertEqual(400, self.client.open(
                        "/api/early-access", method=method, json=body
                    ).status_code)
            self.assertEqual(400, self.client.open(
                "/api/early-access", method=method, data="{", content_type="application/json"
            ).status_code)
            self.assertEqual(400, self.client.open(
                "/api/early-access?subject=" + self.subject,
                method=method, json={"subject": self.subject},
            ).status_code)
        self.assertFalse(os.path.exists(self.db_path))

    def test_membership_database_failure_returns_service_unavailable(self):
        with patch.object(update_app, "early_access_database", side_effect=sqlite3.OperationalError), \
                patch.object(update_app.app.logger, "exception"):
            for method in ("GET", "POST", "DELETE"):
                self.assertEqual(503, self.membership(method).status_code)

    def test_legacy_update_uses_stable_metadata_and_does_not_create_database(self):
        stable_path = self.write_package("stable", 10)
        self.write_package("preview", 20)
        response = self.check_update()
        self.assertEqual(200, response.status_code)
        self.assertEqual({
            "versionCode": 10,
            "versionName": "stable-10",
            "force": False,
            "url": f"{update_app.HOST_URL}/download/ahutong.apk",
            "sha256": hashlib.sha256(b"stable").hexdigest(),
            "signature": "test-signature",
            "alg": "ed25519",
            "changelog": "stable changelog",
            "channel": "stable",
            "note": "Latest APK update",
        }, response.get_json())
        self.sign_file.assert_called_once_with(stable_path)
        self.assertFalse(os.path.exists(self.db_path))

    def test_nonmember_can_only_receive_stable(self):
        self.write_package("stable", 10)
        self.write_package("preview", 20)
        response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("stable", response.get_json()["channel"])
        self.assertEqual(10, response.get_json()["versionCode"])

    def test_member_receives_highest_version_with_stable_winning_ties(self):
        self.membership("POST")
        for stable_version, preview_version, expected_channel in (
            (10, 20, "preview"), (30, 20, "stable"), (20, 20, "stable")
        ):
            with self.subTest(stable=stable_version, preview=preview_version):
                stable_path = self.write_package("stable", stable_version)
                preview_path = self.write_package("preview", preview_version)
                self.sign_file.reset_mock()
                response = self.check_update(self.subject)
                self.assertEqual(200, response.status_code)
                payload = response.get_json()
                self.assertEqual(expected_channel, payload["channel"])
                self.assertEqual(max(stable_version, preview_version), payload["versionCode"])
                self.assertEqual(f"{expected_channel} changelog", payload["changelog"])
                self.assertEqual(expected_channel == "preview", payload["force"])
                expected_path = preview_path if expected_channel == "preview" else stable_path
                self.sign_file.assert_called_once_with(expected_path)
                expected_url = "preview/ahutong.apk" if expected_channel == "preview" else "ahutong.apk"
                self.assertEqual(f"{update_app.HOST_URL}/download/{expected_url}", payload["url"])

    def test_corrupt_preview_optional_metadata_falls_back_to_complete_stable_config(self):
        self.membership("POST")
        for filename in (
            update_app.APK_VERSION_NAME_FILENAME,
            update_app.APK_CHANGELOG_FILENAME,
            update_app.APK_FORCE_FILENAME,
        ):
            with self.subTest(filename=filename):
                stable_path = self.write_package("stable", 10)
                self.write_package("preview", 20)
                with open(os.path.join(self.static_dir, "preview", filename), "wb") as file:
                    file.write(b"\xff\xfe")
                self.sign_file.reset_mock()
                with patch.object(update_app.app.logger, "warning"):
                    response = self.check_update(self.subject)
                self.assertEqual(200, response.status_code)
                self.assertEqual({
                    "versionCode": 10,
                    "versionName": "stable-10",
                    "force": False,
                    "url": f"{update_app.HOST_URL}/download/ahutong.apk",
                    "sha256": hashlib.sha256(b"stable").hexdigest(),
                    "signature": "test-signature",
                    "alg": "ed25519",
                    "changelog": "stable changelog",
                    "channel": "stable",
                    "note": "Latest APK update",
                }, response.get_json())
                self.sign_file.assert_called_once_with(stable_path)

    def test_unreadable_preview_apk_falls_back_to_stable(self):
        stable_path = self.write_package("stable", 10)
        preview_path = self.write_package("preview", 20)
        self.membership("POST")
        real_hash = update_app.sha256_hex_file

        def read_hash(path):
            if path == preview_path:
                raise PermissionError("preview APK cannot be read")
            return real_hash(path)

        with patch.object(update_app, "sha256_hex_file", side_effect=read_hash), \
                patch.object(update_app.app.logger, "warning"):
            response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("stable", response.get_json()["channel"])
        self.assertEqual(hashlib.sha256(b"stable").hexdigest(), response.get_json()["sha256"])
        self.sign_file.assert_called_once_with(stable_path)

    def test_preview_signing_failure_falls_back_to_stable(self):
        stable_path = self.write_package("stable", 10)
        preview_path = self.write_package("preview", 20)
        self.membership("POST")
        self.sign_file.side_effect = [RuntimeError("preview signing failed"), "stable-signature"]
        with patch.object(update_app.app.logger, "warning"):
            response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("stable", response.get_json()["channel"])
        self.assertEqual("stable-signature", response.get_json()["signature"])
        self.assertEqual(
            [preview_path, stable_path],
            [args.args[0] for args in self.sign_file.call_args_list],
        )

    def test_all_candidate_build_failures_keep_internal_server_error(self):
        stable_path = self.write_package("stable", 10)
        preview_path = self.write_package("preview", 20)
        self.membership("POST")
        self.sign_file.side_effect = RuntimeError("signing unavailable")
        with patch.object(update_app.app.logger, "warning"):
            response = self.check_update(self.subject)
        self.assertEqual(500, response.status_code)
        self.assertIn("Failed to build apk update config", response.get_json()["error"])
        self.assertEqual(
            [preview_path, stable_path],
            [args.args[0] for args in self.sign_file.call_args_list],
        )

    def test_nonmember_never_receives_preview_when_stable_metadata_is_broken(self):
        self.write_package("stable", 10)
        self.write_package("preview", 20)
        with open(os.path.join(self.static_dir, update_app.APK_CHANGELOG_FILENAME), "wb") as file:
            file.write(b"\xff\xfe")
        for subject in (None, self.subject):
            with self.subTest(subject=subject):
                self.assertEqual(500, self.check_update(subject).status_code)
        self.sign_file.assert_not_called()

    def test_healthy_newer_stable_does_not_read_broken_lower_preview_metadata(self):
        stable_path = self.write_package("stable", 30)
        self.write_package("preview", 20)
        self.membership("POST")
        with open(os.path.join(self.static_dir, "preview", update_app.APK_CHANGELOG_FILENAME), "wb") as file:
            file.write(b"\xff\xfe")
        response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("stable", response.get_json()["channel"])
        self.assertEqual(30, response.get_json()["versionCode"])
        self.sign_file.assert_called_once_with(stable_path)

    def test_member_can_use_preview_when_newer_stable_cannot_be_built(self):
        stable_path = self.write_package("stable", 30)
        preview_path = self.write_package("preview", 20)
        self.membership("POST")
        self.sign_file.side_effect = [RuntimeError("stable signing failed"), "preview-signature"]
        with patch.object(update_app.app.logger, "warning"):
            response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("preview", response.get_json()["channel"])
        self.assertEqual(20, response.get_json()["versionCode"])
        self.assertEqual("preview-signature", response.get_json()["signature"])
        self.assertEqual(
            [stable_path, preview_path],
            [args.args[0] for args in self.sign_file.call_args_list],
        )

    def test_leaving_restores_stable_update_channel(self):
        self.write_package("stable", 10)
        self.write_package("preview", 20)
        self.membership("POST")
        self.assertEqual("preview", self.check_update(self.subject).get_json()["channel"])
        self.membership("DELETE")
        self.assertEqual("stable", self.check_update(self.subject).get_json()["channel"])

    def test_preview_without_stable_is_available_only_to_members(self):
        self.write_package("preview", 20)
        self.assertEqual(404, self.check_update().status_code)
        self.assertEqual(404, self.check_update(self.subject).status_code)
        self.membership("POST")
        response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("preview", response.get_json()["channel"])

    def test_missing_required_preview_files_fall_back_to_stable(self):
        self.write_package("stable", 10)
        self.membership("POST")
        for filename in (update_app.APK_FILENAME, update_app.APK_VERSION_FILENAME):
            with self.subTest(filename=filename):
                self.write_package("preview", 20)
                os.remove(os.path.join(self.static_dir, "preview", filename))
                response = self.check_update(self.subject)
                self.assertEqual(200, response.status_code)
                self.assertEqual("stable", response.get_json()["channel"])

    def test_no_available_package_returns_original_not_found_response(self):
        for joined in (False, True):
            with self.subTest(joined=joined):
                if joined:
                    self.membership("POST")
                response = self.check_update(self.subject)
                self.assertEqual(404, response.status_code)
                self.assertEqual({"error": "Server files missing"}, response.get_json())
        self.sign_file.assert_not_called()

    def test_missing_optional_metadata_preserves_legacy_defaults(self):
        self.write_package("stable", 10, optional_metadata=False)
        payload = self.check_update().get_json()
        self.assertEqual("10", payload["versionName"])
        self.assertEqual("New version available.", payload["changelog"])
        self.assertFalse(payload["force"])

    def test_update_rejects_invalid_or_duplicate_subject(self):
        self.write_package("stable", 10)
        for subject in ("", "a" * 63, self.subject.upper()):
            self.assertEqual(400, self.check_update(subject).status_code)
        self.assertEqual(400, self.client.get(
            "/api/check_apk_update", query_string=[("subject", self.subject), ("subject", self.subject)]
        ).status_code)
        self.assertFalse(os.path.exists(self.db_path))
        self.sign_file.assert_not_called()

    def test_update_database_failure_falls_back_to_stable(self):
        self.write_package("stable", 10)
        self.write_package("preview", 20)
        with patch.object(update_app, "is_early_access_member", side_effect=sqlite3.OperationalError), \
                patch.object(update_app.app.logger, "exception"):
            response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("stable", response.get_json()["channel"])

    def test_invalid_preview_version_does_not_block_stable_update(self):
        self.write_package("stable", 10)
        self.write_package("preview", "invalid")
        self.membership("POST")
        with self.assertLogs(update_app.app.logger, level="WARNING"):
            response = self.check_update(self.subject)
        self.assertEqual(200, response.status_code)
        self.assertEqual("stable", response.get_json()["channel"])


if __name__ == "__main__":
    unittest.main()
