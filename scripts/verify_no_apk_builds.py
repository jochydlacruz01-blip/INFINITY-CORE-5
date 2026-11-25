#!/usr/bin/env python3
"""Fail CI if the active development workflow starts producing APKs."""
from __future__ import annotations

import re
import sys
from pathlib import Path

FORBIDDEN_PATTERNS = {
    "debug APK task": re.compile(r"\bassembleDebug\b", re.IGNORECASE),
    "release APK task": re.compile(r"\bassembleRelease\b", re.IGNORECASE),
    "APK artifact publication": re.compile(r"(?:name|path):[^\n]*(?:\.apk|app-(?:debug|release))", re.IGNORECASE),
    "APK signing configuration": re.compile(r"\bsigningConfig\b", re.IGNORECASE),
    "APK output reference": re.compile(r"\b(?:app-)?(?:debug|release)[^\n]*\.apk\b", re.IGNORECASE),
}


def audit_workflow(path: str | Path) -> list[str]:
    workflow = Path(path)
    try:
        content = workflow.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"Cannot read workflow: {exc}"]

    findings = []
    for label, pattern in FORBIDDEN_PATTERNS.items():
        if pattern.search(content):
            findings.append(f"Development workflow contains forbidden {label}")
    return findings


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".github/workflows/android-pr.yml")
    errors = audit_workflow(target)
    if errors:
        print("\n".join("FAIL: " + error for error in errors))
        raise SystemExit(1)
    print("Source-only workflow policy passed: no APK build, signing, or publication steps")
