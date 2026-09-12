import os
import tempfile
import unittest

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


if __name__ == "__main__":
    unittest.main()
