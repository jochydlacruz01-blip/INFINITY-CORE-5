#!/usr/bin/env python3
"""Fail closed on release APKs; allow one tightly scoped manual DEBUG test APK."""
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
    r"\bAPK(?:_[A-Z0-9_]+|Path|File)\b)",
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
ALLOWED_LINT_REPORTS = ("app/build/reports/lint-results", "freerdp-core/build/reports/lint-results")
ALLOWED_NON_APK_ARTIFACT_PATHS = (
    "artifacts/infinity-windows-companion/",
    "artifacts/emulator-screenshots/",
    "artifacts/test-results/",
)
TEMPORARILY_DISABLED_APK_WORKFLOWS = {"android.yml", "publish-release.yml"}
DEBUG_APK_PATH = "app/build/outputs/apk/debug/app-debug.apk"


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
    if any(value in (root, root + "**") for root in ALLOWED_NON_APK_ARTIFACT_PATHS):
        return True
    if any(value.startswith(root) and value.lower().endswith(".html") for root in ALLOWED_LINT_REPORTS):
        return True
    if not any(value.startswith(root) for root in ALLOWED_REPORT_PATHS):
        return False
    if value.endswith("/**"):
        return True
    return value.lower().endswith((".xml", ".html"))


def _all_jobs_explicitly_disabled(content: str) -> bool:
    jobs_match = re.search(r"(?ms)^jobs\s*:\s*\n(.*)\Z", content)
    if not jobs_match:
        return False
    jobs_section = jobs_match.group(1)
    headers = list(re.finditer(r"(?m)^  [A-Za-z0-9_-]+\s*:\s*$", jobs_section))
    if not headers:
        return False
    for index, header in enumerate(headers):
        end = headers[index + 1].start() if index + 1 < len(headers) else len(jobs_section)
        block = jobs_section[header.start():end]
        if not re.search(r"(?m)^    if\s*:\s*false(?:\s+#.*)?\s*$", block):
            return False
    return True


def _is_temporarily_disabled_apk_workflow(path: Path, content: str) -> bool:
    return (
        path.name in TEMPORARILY_DISABLED_APK_WORKFLOWS
        and re.search(r"(?m)^on\s*:\s*\n\s+workflow_dispatch\s*:", content) is not None
        and _all_jobs_explicitly_disabled(content)
    )


def _is_debug_only_test_apk_workflow(path: Path, content: str) -> bool:
    """Allow only manual DEBUG assembly and two exact, narrowly scoped artifacts."""
    if path.name != "android.yml":
        return False
    required = (
        re.search(r"(?m)^on:\s*$", content),
        re.search(r"(?m)^\s+workflow_dispatch:\s*$", content),
        re.search(r"(?m)^\s+contents:\s*read\s*$", content),
        re.search(r"(?i)gradle\s+compileDebugKotlin\s+testDebugUnitTest\s+lintDebug\s+assembleDebug", content),
        re.search(r"(?im)^\s+name:\s*PROJECT-INFINITY-0\.10-DEBUG-TEST-APK\s*$", content),
        re.search(r"(?im)^\s+path:\s*" + re.escape(DEBUG_APK_PATH) + r"\s*$", content),
    )
    if not all(required):
        return False
    forbidden = (
        r"assembleRelease|testReleaseUnitTest|lintRelease|bundleRelease|signingConfig|signingConfigs|"
        r"INFINITY_KEYSTORE|jarsigner|softprops/action-gh-release|gh\s+release|"
        r"app/build/outputs/apk/release|app-release\.apk|contents:\s*write"
    )
    if re.search(forbidden, content, re.IGNORECASE):
        return False
    blocks = _artifact_steps(content)
    if len(blocks) != 2:
        return False
    apk_block_ok = any(
        re.search(r"(?im)^\s+name:\s*PROJECT-INFINITY-0\.10-DEBUG-TEST-APK\s*$", block)
        and re.search(r"(?im)^\s+path:\s*" + re.escape(DEBUG_APK_PATH) + r"\s*$", block)
        for block in blocks
    )
    checksum_block_ok = any(
        re.search(r"(?im)^\s+name:\s*PROJECT-INFINITY-0\.10-DEBUG-APK-CHECKSUM\s*$", block)
        and re.search(r"(?im)^\s+path:\s*app-debug-sha256\.txt\s*$", block)
        for block in blocks
    )
    return apk_block_ok and checksum_block_ok


def _audit_file(workflow: Path) -> list[str]:
    try:
        content = workflow.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{workflow}: cannot read workflow: {exc}"]

    disabled_apk_workflow = _is_temporarily_disabled_apk_workflow(workflow, content)
    debug_only_test_workflow = _is_debug_only_test_apk_workflow(workflow, content)

    findings: list[str] = []
    normalized_shell = content.replace("\\\n", " ")
    gradle_build = any(
        GRADLE_INVOKED.search(line) and FORBIDDEN_GRADLE_TASK.search(line)
        for line in normalized_shell.splitlines()
    )
    has_apk_output = any(
        pattern.search(content)
        for pattern in (APK_REFERENCE, GENERIC_OUTPUT, SIGNING, RELEASE_UPLOAD)
    )
    if (gradle_build or has_apk_output) and not (disabled_apk_workflow or debug_only_test_workflow):
        findings.append(f"{workflow}: APK build, signing, or publication route is not disabled or explicitly DEBUG-only")

    if not (disabled_apk_workflow or debug_only_test_workflow):
        for block in _artifact_steps(content):
            path_match = re.search(
                r"(?ims)^\s*path\s*:\s*(.*?)(?=^\s*[a-zA-Z_-]+\s*:|\Z)",
                block,
            )
            name_match = re.search(r"(?im)^\s*name:\s*(.*?)\s*$", block)
            path_value = path_match.group(1).strip() if path_match else ""
            name_value = name_match.group(1).strip() if name_match else ""

            if APK_REFERENCE.search(name_value) or APK_REFERENCE.search(path_value):
                findings.append(f"{workflow}: artifact upload references an APK name/path/output")
            elif GENERIC_OUTPUT.search(path_value):
                findings.append(f"{workflow}: artifact upload references generic build/outputs")
            elif path_value:
                entries = [
                    line.strip().strip("'\"")
                    for line in path_value.splitlines()
                    if line.strip() and line.strip() not in ("|", ">")
                ]
                if not entries or any(not _path_is_narrow_test_report(item) for item in entries):
                    findings.append(f"{workflow}: artifact upload path is broad, dynamic, or not an allowlisted test report")
            else:
                findings.append(f"{workflow}: artifact upload has no explicit allowlisted test-report path")

    return findings


def audit_workflow(path: str | Path) -> list[str]:
    target = Path(path)
    if not target.exists():
        return [f"Cannot find workflow target: {target}"]
    if target.is_dir():
        workflows = sorted(
            item for item in target.rglob("*")
            if item.is_file() and item.suffix.lower() in (".yml", ".yaml")
        )
        findings = [finding for workflow in workflows for finding in _audit_file(workflow)]
        return sorted(findings)
    return sorted(_audit_file(target))


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".github/workflows")
    errors = audit_workflow(target)
    if errors:
        print("\n".join("FAIL: " + error for error in errors))
        raise SystemExit(1)
    print("Workflow policy passed: release APKs remain disabled; only the explicitly scoped manual DEBUG test APK is allowed")
