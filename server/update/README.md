# AHUTong update service

The production Flask entry point is tracked in `app.py`. Deploy changes through a temporary file, validate them, and replace the live file atomically.

## Early access membership

Membership uses a signed-in account's lowercase SHA-256 hash of the UTF-8 string `user:<student number>`. Do not send the raw student number or a visitor/device subject. The server validates a 64-character lowercase hexadecimal subject and stores only that hash in the SQLite `early_access_members` table.

- `GET /api/early-access?subject=<hash>` returns `{"joined": false}` for a nonmember and `{"joined": true}` for a member.
- `POST /api/early-access` accepts only the JSON object `{"subject": "<hash>"}` and joins idempotently.
- `DELETE /api/early-access` accepts the same JSON object and removes membership idempotently.

Missing, malformed, duplicate query subjects and extra membership fields return HTTP 400. An unavailable membership database returns HTTP 503 for these endpoints. Membership is retained across app reinstalls because it belongs to the account hash. The default database path is `early_access.sqlite3` beside `app.py`, configured by `app.config["DB_PATH"]`; retain and back up this file when updating the service, and keep it out of source control. Tables are created lazily on first use.

## APK update channels

`GET /api/check_apk_update` retains the existing stable update behavior and URL. Guests omit `subject`. Signed-in clients may add `?subject=<account hash>`: nonmembers still receive only stable updates; members receive the highest available `versionCode` across stable and preview, with stable winning equal versions. The response adds `channel`, either `stable` or `preview`, and otherwise keeps the existing fields, checksum and signature format. Candidates are attempted in descending version order, preferring stable on ties. Only attempted candidates are hashed and signed. Membership database failures fall back to stable updates.

Stable packages keep the existing `static/` paths. Preview packages use a separate directory:

```text
static/
  ahutong.apk
  apk_version.txt
  apk_version_name.txt
  apk_changelog.txt
  apk_force.txt
  preview/
    ahutong.apk
    apk_version.txt
    apk_version_name.txt
    apk_changelog.txt
    apk_force.txt
```

Both channels require an APK and integer `apk_version.txt`. The other metadata files remain optional with the existing defaults. Preview downloads use `/download/preview/ahutong.apk`; publishing preview files must not replace the stable files. A missing or invalid member-channel candidate is skipped. If building the selected candidate fails while reading metadata, hashing, or signing, the service tries the next eligible candidate; a broken preview therefore cannot block a usable stable update. Nonmembers never fall back to preview. No discovered package returns the original HTTP 404 `{"error": "Server files missing"}`; if all discovered candidates fail to build, the service retains HTTP 500. Leaving early access makes subsequent checks choose stable; it does not install an older APK or uninstall the current app.

## School calendars

The legacy `GET /download/xiaoli.jpg` endpoint remains unchanged. Versioned calendars use:

- `GET /api/school_calendars` to list available academic years.
- `GET /api/school_calendars/<YYYY-YYYY>` to download one JPEG.

Calendar images belong in `static/calendars/` and must use a consecutive academic-year filename such as `2026-2027.jpg`. The list endpoint scans actual files, ignores invalid names, removes the need for a separately maintained index, and returns newest years first. Gaps between available academic years are supported: a missing file such as `2023-2024.jpg` is simply omitted from the response.

Run the isolated server tests with the production virtual environment before deployment:

```bash
python -m unittest -v test_app.py
```

The tests use temporary package directories and a temporary SQLite database, and mock signing without reading a private key. They cover membership validation, idempotent join/leave, account isolation, channel selection, corrupt optional metadata, APK read/signing failures, fallback eligibility, all-candidate failures and existing update compatibility, as well as calendars.

生产部署可设置 `AHUTONG_EARLY_ACCESS_DB_PATH` 指向站点目录之外的数据库文件；例如 `/var/lib/openahu-update/early_access.sqlite3`。目录需由服务运行账号可写，并限制其他账号读取。
