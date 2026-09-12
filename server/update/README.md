# AHUTong update service

The production Flask entry point is tracked in `app.py`. Deploy changes through a temporary file, validate them, and replace the live file atomically.

## School calendars

The legacy `GET /download/xiaoli.jpg` endpoint remains unchanged. Versioned calendars use:

- `GET /api/school_calendars` to list available academic years.
- `GET /api/school_calendars/<YYYY-YYYY>` to download one JPEG.

Calendar images belong in `static/calendars/` and must use a consecutive academic-year filename such as `2026-2027.jpg`. The list endpoint scans actual files, ignores invalid names, removes the need for a separately maintained index, and returns newest years first. Gaps between available academic years are supported: a missing file such as `2023-2024.jpg` is simply omitted from the response.

Run the isolated server tests with the production virtual environment before deployment:

```bash
python -m unittest -v test_app.py
```
