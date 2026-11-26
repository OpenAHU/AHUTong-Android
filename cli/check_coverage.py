"""Fail when a new App page/widget is not reachable through the CLI catalog.

This checks entry coverage, not headless business parity or online availability.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


def check(root):
    main = root / "app/src/main/java/com/ahu/ahutong"
    bridge = root / "app/src/debug/java/com/ahu/ahutong/cli/CliBridge.kt"
    source = bridge.read_text(encoding="utf-8-sig")
    routes_body = source.split("private fun routes()", 1)[1].split("private fun open", 1)[0]
    explicit = set(re.findall(r'"([^"\n]+)"', routes_body))
    catalog = (main / "personalization/action/AppAction.kt").read_text(encoding="utf-8-sig")
    known = explicit | set(re.findall(r'spec\(AppActionId\.\w+, ActionFamily\.\w+, "([^"\n]+)"', catalog))
    widgets = (main / "ui" / "screen" / "main" / "home" / "HomeWidgetRegistry.kt").read_text(encoding="utf-8-sig")
    known |= set(re.findall(r'route\s*=\s*"([^"\n]+)"', widgets))
    nav = (main / "ui/screen/Main.kt").read_text(encoding="utf-8-sig")
    definitions = nav + (root / "feature/repository-index/src/main/java/com/ahu/ahutong/ui/screen/main/RepositoryRoutes.kt").read_text(encoding="utf-8-sig")
    constants = dict(re.findall(r'const val (\w+)\s*=\s*"([^"\n]+)"', definitions))
    destinations = re.findall(r'animatedComposable\(\s*(?:route\s*=\s*)?(?:"([^"\n]+)"|([A-Z_]+))', nav)
    pages = {literal or constants.get(reference, "UNRESOLVED:" + reference) for literal, reference in destinations}
    missing = sorted(pages - known)
    errors = ["CLI page missing: " + item for item in missing]
    if "AssistantCapabilities.all(" not in source:
        errors.append("CLI must use the live App capability registry")
    if "HomeWidgetRegistry.widgets" not in source or "AppActionCatalog.specs" not in routes_body:
        errors.append("CLI must discover App widgets and actions dynamically")
    android = "{http://schemas.android.com/apk/res/android}"
    debug_manifest = ET.parse(root / "app/src/debug/AndroidManifest.xml").getroot()
    providers = [p for p in debug_manifest.findall("application/provider") if p.get(android + "name", "").endswith("CliProvider")]
    if len(providers) != 1 or providers[0].get(android + "permission") != "android.permission.DUMP":
        errors.append("Debug CLI provider must require DUMP permission")
    release_manifests = [root / "app/src/main/AndroidManifest.xml", root / "app/src/release/AndroidManifest.xml"]
    for manifest in release_manifests:
        if manifest.exists() and "CliProvider" in manifest.read_text(encoding="utf-8-sig"):
            errors.append("CLI provider must not be present in a release manifest")
    operations = re.findall(r'op\("([^"]+)"', source)
    if len(operations) != len(set(operations)):
        errors.append("Duplicate CLI operation names")
    return errors, len(pages), len(operations)


if __name__ == "__main__":
    problems, pages, operations = check(Path(__file__).resolve().parents[1])
    for problem in problems:
        print(problem, file=sys.stderr)
    print("Checked %d App destinations and %d business/UI operations; entry coverage is not business parity." % (pages, operations))
    sys.exit(bool(problems))
