#!/usr/bin/env python3
"""Static security gate for the Android manifest; intentionally dependency-free."""
from __future__ import annotations
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"


def attr(node: ET.Element, name: str) -> str | None:
    return node.get(ANDROID + name)


def audit_manifest(path: str | Path) -> list[str]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        return [f"Manifest cannot be parsed: {exc}"]

    errors: list[str] = []
    app = root.find("application")
    if app is None:
        return ["Missing application element"]

    if attr(app, "allowBackup") != "false":
        errors.append("application must set android:allowBackup=false")
    if attr(app, "usesCleartextTraffic") != "false":
        errors.append("application must set android:usesCleartextTraffic=false")
    if attr(app, "debuggable") == "true":
        errors.append("application must not enable android:debuggable")

    for component in list(app):
        if component.tag not in {"activity", "activity-alias", "service", "receiver", "provider"}:
            continue
        name = attr(component, "name") or component.tag
        exported = attr(component, "exported")
        if exported not in {"true", "false"}:
            errors.append(f"{name} must explicitly declare android:exported")
            continue
        if exported != "true":
            continue

        permission = attr(component, "permission")
        if component.tag == "service" and permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE":
            continue

        has_launcher = any(
            any(attr(action, "name") == "android.intent.action.MAIN" for action in intent.findall("action"))
            and any(attr(category, "name") == "android.intent.category.LAUNCHER" for category in intent.findall("category"))
            for intent in component.findall("intent-filter")
        )
        if component.tag == "activity" and has_launcher:
            continue
        if not permission:
            errors.append(f"exported component {name} must require an explicit permission")
    return errors


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/src/main/AndroidManifest.xml")
    findings = audit_manifest(target)
    if findings:
        print("\n".join("FAIL: " + item for item in findings))
        raise SystemExit(1)
    print("Android manifest security audit passed")
