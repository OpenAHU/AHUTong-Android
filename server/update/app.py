import os
import hashlib
import base64
import json
import ipaddress
import re
from flask import Flask, abort, jsonify, send_from_directory, request
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519

app = Flask(__name__)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Keys
PRIVATE_KEY_PATH = os.path.join(BASE_DIR, "AHUTong_ed25519_private.pem")

# Static files dir
FILE_DIR = os.path.join(BASE_DIR, "static")
CALENDAR_DIR = os.path.join(FILE_DIR, "calendars")
CALENDAR_YEAR_PATTERN = re.compile(r"^(\d{4})-(\d{4})$")

# Filenames
SO_FILENAME = "libahutong_rs.so"
APK_FILENAME = "ahutong.apk"  # 你可以换成带版本号的文件名，例如 "ahutong-3.1.0.apk"

SO_VERSION_FILENAME = "so_version.txt"
APK_VERSION_FILENAME = "apk_version.txt"

# Optional metadata files (不存在也没关系)
APK_VERSION_NAME_FILENAME = "apk_version_name.txt"   # e.g. 3.1.0
APK_CHANGELOG_FILENAME = "apk_changelog.txt"         # multiline text
APK_FORCE_FILENAME = "apk_force.txt"                 # "1" / "true" => force
GRAY_CONFIG_FILENAME = "gray_config.json"

HOST_URL = "https://openahu.org"

# Only trust proxy headers when the direct peer is the local Nginx instance.
TRUSTED_PROXY_IPS = {"127.0.0.1", "::1"}

# Public endpoints exposed by this service. Everything else is rejected with 403.
ALLOWED_ENDPOINTS = {
    "check_update_so": {"GET"},
    "check_update_apk": {"GET"},
    "check_gray_feature": {"GET"},
    "list_school_calendars": {"GET"},
    "get_school_calendar": {"GET"},
    "download_file": {"GET"},
}


# -------------------------
# Helpers
# -------------------------

def _valid_ip(value: str):
    """Return a normalized IP string, or None when the value is invalid."""
    try:
        return str(ipaddress.ip_address(value.strip()))
    except (ValueError, AttributeError):
        return None


def get_client_ip() -> str:
    """
    Get the real client IP when Flask is behind a local Nginx reverse proxy.

    Security rule: forwarded headers are trusted only when request.remote_addr
    is one of TRUSTED_PROXY_IPS. This prevents Internet clients from spoofing
    X-Real-IP / X-Forwarded-For when Flask is accidentally exposed directly.
    """
    peer_ip = _valid_ip(request.remote_addr or "") or "unknown"

    if peer_ip not in TRUSTED_PROXY_IPS:
        return peer_ip

    # Prefer X-Real-IP. Recommended Nginx setting:
    #   proxy_set_header X-Real-IP $remote_addr;
    real_ip = _valid_ip(request.headers.get("X-Real-IP", ""))
    if real_ip:
        return real_ip

    # Fallback for common proxy configurations. The first address is the
    # original client when Nginx sets X-Forwarded-For with $proxy_add_x_forwarded_for.
    forwarded_for = request.headers.get("X-Forwarded-For", "")
    if forwarded_for:
        first_ip = _valid_ip(forwarded_for.split(",", 1)[0])
        if first_ip:
            return first_ip

    return peer_ip


@app.before_request
def reject_unknown_requests():
    """Allow only explicitly declared public endpoints/methods; deny all else."""
    endpoint = request.endpoint
    allowed_methods = ALLOWED_ENDPOINTS.get(endpoint)

    if allowed_methods is None or request.method not in allowed_methods:
        client_ip = get_client_ip()
        app.logger.warning(
            "Blocked request: ip=%s method=%s path=%s endpoint=%s",
            client_ip,
            request.method,
            request.path,
            endpoint,
        )
        return jsonify({"error": "Forbidden"}), 403



def load_private_key():
    if not os.path.exists(PRIVATE_KEY_PATH):
        raise RuntimeError("Private key not found")
    with open(PRIVATE_KEY_PATH, "rb") as f:
        return serialization.load_pem_private_key(
            f.read(),
            password=None
        )

def sha256_hex_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()

def sha256_raw_file(path: str) -> bytes:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.digest()

def sign_file_sha256_digest(path: str) -> str:
    """
    与你 Rust 侧逻辑一致：对文件 sha256 digest（raw bytes）进行 ed25519 签名，然后 base64
    """
    private_key = load_private_key()
    digest = sha256_raw_file(path)  # raw digest bytes
    signature = private_key.sign(digest)
    return base64.b64encode(signature).decode("utf-8")

def read_int_file(path: str, default: int = None) -> int:
    if not os.path.exists(path):
        if default is None:
            raise FileNotFoundError(path)
        return default
    with open(path, "r", encoding="utf-8") as f:
        return int(f.read().strip())

def read_text_file(path: str, default: str = "") -> str:
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return f.read().strip()

def read_bool_file(path: str, default: bool = False) -> bool:
    if not os.path.exists(path):
        return default
    val = read_text_file(path, "").strip().lower()
    return val in ("1", "true", "yes", "y", "on")


def load_gray_config() -> dict:
    path = os.path.join(BASE_DIR, GRAY_CONFIG_FILENAME)
    if not os.path.exists(path):
        return {"features": {}}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def rollout_bucket(feature: str, subject: str) -> int:
    digest = hashlib.sha256(f"{feature}:{subject}".encode("utf-8")).digest()
    return int.from_bytes(digest[:4], "big") % 100

def clamp_rollout_percentage(value, default: int = 0) -> int:
    try:
        return max(0, min(100, int(value)))
    except (TypeError, ValueError):
        return default

def string_set(value) -> set:
    if not isinstance(value, list):
        return set()
    return {str(item).strip() for item in value if str(item).strip()}


def parse_calendar_year(value: str):
    match = CALENDAR_YEAR_PATTERN.fullmatch(value)
    if not match:
        return None
    start_year, end_year = (int(part) for part in match.groups())
    if end_year != start_year + 1:
        return None
    return start_year, end_year


def available_calendar_years() -> list[str]:
    if not os.path.isdir(CALENDAR_DIR):
        return []

    calendars = []
    for filename in os.listdir(CALENDAR_DIR):
        if not filename.endswith(".jpg"):
            continue
        year = filename[:-4]
        parsed = parse_calendar_year(year)
        if parsed is None:
            continue
        path = os.path.join(CALENDAR_DIR, filename)
        if os.path.isfile(path):
            calendars.append((parsed[0], year))

    calendars.sort(key=lambda item: item[0], reverse=True)
    return [year for _, year in calendars]


# -------------------------
# API: so hot update
# -------------------------

@app.route("/api/check_update", methods=["GET"])
def check_update_so():
    so_path = os.path.join(FILE_DIR, SO_FILENAME)
    version_path = os.path.join(FILE_DIR, SO_VERSION_FILENAME)

    if not os.path.exists(so_path) or not os.path.exists(version_path):
        return jsonify({"error": "Server files missing"}), 404

    try:
        version_code = read_int_file(version_path)
        sha256_hex = sha256_hex_file(so_path)
        signature = sign_file_sha256_digest(so_path)
    except Exception as e:
        return jsonify({"error": f"Failed to build so update config: {e}"}), 500

    return jsonify({
        "version": version_code,
        "url": f"{HOST_URL}/download/{SO_FILENAME}",
        "sha256": sha256_hex,
        "signature": signature,
        "alg": "ed25519",
        "note": "Latest SO update"
    })


# -------------------------
# API: apk update
# -------------------------

@app.route("/api/check_apk_update", methods=["GET"])
def check_update_apk():
    apk_path = os.path.join(FILE_DIR, APK_FILENAME)
    version_path = os.path.join(FILE_DIR, APK_VERSION_FILENAME)

    if not os.path.exists(apk_path) or not os.path.exists(version_path):
        return jsonify({"error": "Server files missing"}), 404

    try:
        version_code = read_int_file(version_path)

        version_name_path = os.path.join(FILE_DIR, APK_VERSION_NAME_FILENAME)
        changelog_path = os.path.join(FILE_DIR, APK_CHANGELOG_FILENAME)
        force_path = os.path.join(FILE_DIR, APK_FORCE_FILENAME)

        version_name = read_text_file(version_name_path, default=str(version_code))
        changelog = read_text_file(changelog_path, default="New version available.")
        force = read_bool_file(force_path, default=False)

        sha256_hex = sha256_hex_file(apk_path)
        signature = sign_file_sha256_digest(apk_path)
    except Exception as e:
        return jsonify({"error": f"Failed to build apk update config: {e}"}), 500

    return jsonify({
        # 你 Kotlin/Rust 侧可以直接用 versionCode 做比较
        "versionCode": version_code,
        "versionName": version_name,

        # 可选：强更开关（你也可以后续加 minSupportedCode）
        "force": force,

        "url": f"{HOST_URL}/download/{APK_FILENAME}",
        "sha256": sha256_hex,
        "signature": signature,
        "alg": "ed25519",

        "changelog": changelog,
        "note": "Latest APK update"
    })


# -------------------------
# API: gray rollout
# -------------------------


# Usage:
# - Edit gray_config.json to control gray rollout without redeploying the Android app.
# - features.<feature>.enabled=false closes the feature for normal users.
# - rolloutPercentage is clamped to 0..100 and applies when enabled=true.
# - forceDisabledSubjects has highest priority, then forceEnabledSubjects, then rollout bucket.
# - subject is expected to be the client-side SHA-256 identifier, not a raw student/device id.
# Example gray_config.json:
# {
#   "features": {
#     "home_edit": {
#       "enabled": true,
#       "rolloutPercentage": 20,
#       "forceEnabledSubjects": [],
#       "forceDisabledSubjects": []
#     }
#   }
# }

@app.route("/api/gray/check", methods=["GET"])
def check_gray_feature():
    feature = request.args.get("feature", "").strip()
    subject = request.args.get("subject", "").strip()

    if not feature or not subject:
        return jsonify({"error": "feature and subject are required"}), 400

    bucket = rollout_bucket(feature, subject)
    config = load_gray_config()
    features = config.get("features", {}) if isinstance(config, dict) else {}
    item = features.get(feature) if isinstance(features, dict) else None

    if not isinstance(item, dict):
        return jsonify({
            "feature": feature,
            "enabled": False,
            "rolloutPercentage": 0,
            "bucket": bucket,
            "source": "server",
            "reason": "feature_missing"
        })

    rollout_percentage = clamp_rollout_percentage(item.get("rolloutPercentage"), default=0)
    force_enabled_subjects = string_set(item.get("forceEnabledSubjects"))
    force_disabled_subjects = string_set(item.get("forceDisabledSubjects"))
    globally_enabled = bool(item.get("enabled", False))

    if subject in force_disabled_subjects:
        enabled = False
        reason = "force_disabled"
    elif subject in force_enabled_subjects:
        enabled = True
        reason = "force_enabled"
    else:
        enabled = globally_enabled and bucket < rollout_percentage
        reason = "rollout" if globally_enabled else "global_disabled"

    return jsonify({
        "feature": feature,
        "enabled": enabled,
        "rolloutPercentage": rollout_percentage,
        "bucket": bucket,
        "source": "server",
        "reason": reason
    })


# -------------------------
# API: school calendars
# -------------------------

@app.route("/api/school_calendars", methods=["GET"])
def list_school_calendars():
    years = available_calendar_years()
    return jsonify({
        "years": years,
        "latestYear": years[0] if years else None,
    })


@app.route("/api/school_calendars/<year>", methods=["GET"])
def get_school_calendar(year):
    if parse_calendar_year(year) is None:
        abort(404)
    return send_from_directory(
        CALENDAR_DIR,
        f"{year}.jpg",
        mimetype="image/jpeg",
    )


# -------------------------
# download static files
# -------------------------

@app.route("/download/<path:filename>", methods=["GET"])
def download_file(filename):
    return send_from_directory(FILE_DIR, filename)


if __name__ == "__main__":
    print("Update Server running...")
    print("SO  check:  /api/check_update")
    print("APK check:  /api/check_apk_update")
    print("Gray check: /api/gray/check")
    print("Calendars:  /api/school_calendars")
    print("Download:   /download/<filename>")
    app.run(host="127.0.0.1", port=5000)
