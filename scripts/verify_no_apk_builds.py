#!/usr/bin/env python3
"""Fail closed when a source-only workflow can build, sign, or publish APKs."""
from __future__ import annotations

import re
import sys
from pathlib import Path

GRADLE_INVOKED = re.compile(r"(?i)(?:^|[\s;&|])(?:\./)?(?:gradlew|gradle)(?:\.bat)?(?:\s|$)")
FORBIDDEN_GRADLE_TASK = re.compile(
    r"(?i)\b(?:assemble\w*|package\w*|bundle\w*|install\w*|build(?:Debug|Release)?|publish\w*)\b"
)
SIGNING = re.compile(r"\b(?:signingConfig|signingConfigs|apksigner|jarsigner)\b", re.IGNORECASE)
APK_REFERENCE = re.compile(
    r"(?:\.apk\b|app[-_](?:debug|release)|(?:^|[/\\])outputs[/\\](?:apk|(?=[^\s]*\.apk))|"
    r"\bAPK(?:_[A-Z0-9]+|Path|File)\b)",
    re.IGNORECASE,
)
GENERIC_OUTPUT = re.compile(r"(?:^|[/\\])(?:app/)?build[/\\]outputs(?:[/\\]|$)", re.IGNORECASE)
DYNAMIC = re.compile(r"\$\{\{.*?\}\}|\$[A-Z_][A-Z0-9_]*", re.IGNORECASE)
UPLOAD_ACTION = re.compile(r"uses\s*:\s*actions/upload-artifact@", re.IGNORECASE)
RELEASE_UPLOAD = re.compile(
    r"(?:softprops/action-gh-release|gh\s+release\s+upload|actions/upload-release-asset)",
    re.IGNORECASE,
)
ALLOWED_REPORT_PATHS = ("app/build/test-results/", "app/build/reports/tests/")


def _artifact_steps(content: str) -> list[str]:
    lines = content.splitlines()
    blocks: list[str] = []
    index = 0
    while index < len(lines):
        if UPLOAD_ACTION.search(lines[index]):
            start = index
            index += 1
            while index < len(lines):
                if re.match(r"\s*-\s+(?:name|uses|run)\s*:", lines[index]):
                    break
                index += 1
            blocks.append("\n".join(lines[start:index]))
        else:
            index += 1
    return blocks


def _path_is_narrow_test_report(path_value: str) -> bool:
    value = path_value.strip().strip("'\"").replace("\\", "/")
    if not value or DYNAMIC.search(value):
        return False
    if not any(value.startswith(root) for root in ALLOWED_REPORT_PATHS):
        return False
    if value.endswith("/**"):
        return True
    return value.lower().endswith((".xml", ".html"))


def audit_workflow(path: str | Path) -> list[str]:
    workflow = Path(path)
    try:
        content = workflow.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"Cannot read workflow: {exc}"]

    findings: list[str] = []
    # Inspect the complete workflow, not only artifact metadata. This catches
    # paths copied/renamed indirectly and non-upload-artifact release publishers.
    if APK_REFERENCE.search(content):
        findings.append("Workflow contains a direct or indirect APK/output reference")
    if GENERIC_OUTPUT.search(content):
        findings.append("Workflow references a generic build/outputs directory")
    if SIGNING.search(content):
        findings.append("Workflow contains APK signing configuration or tooling")
    if RELEASE_UPLOAD.search(content):
        findings.append("Workflow contains a release-asset publication mechanism")

    for line in content.splitlines():
        if GRADLE_INVOKED.search(line) and FORBIDDEN_GRADLE_TASK.search(line):
            findings.append("Workflow invokes a Gradle task capable of building or publishing APKs")

    for block in _artifact_steps(content):
        path_match = re.search(
            r"(?ims)^\s*path\s*:\s*(.*?)(?=^\s*[a-zA-Z_-]+\s*:|\Z)",
            block,
        )
        name_match = re.search(r"(?im)^\s*name\s*:\s*(.*?)\s*$", block)
        path_value = path_match.group(1).strip() if path_match else ""
        name_value = name_match.group(1).strip() if name_match else ""

        if APK_REFERENCE.search(name_value) or APK_REFERENCE.search(path_value):
            findings.append("Artifact upload references an APK name, path, or indirect APK output")
        elif GENERIC_OUTPUT.search(path_value):
            findings.append("Artifact upload references a generic build/outputs directory")
        elif path_value:
            entries = [
                line.strip().strip("'\"")
                for line in path_value.splitlines()
                if line.strip() and line.strip() not in ("|", ">")
            ]
            if not entries or any(not _path_is_narrow_test_report(item) for item in entries):
                findings.append("Artifact upload path is broad, wildcarded, dynamic, or not an allowlisted test report path")
        else:
            findings.append("Artifact upload has no explicit, narrowly scoped test-report path")

    return sorted(set(findings))


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".github/workflows/android-pr.yml")
    errors = audit_workflow(target)
    if errors:
        print("\n".join("FAIL: " + error for error in errors))
        raise SystemExit(1)
    print("Source-only workflow policy passed: no APK build, signing, or publication steps")
