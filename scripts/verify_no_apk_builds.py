#!/usr/bin/env python3
"""Fail closed if any workflow can build or publish APKs during source-only work."""
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
TEMPORARILY_DISABLED_APK_WORKFLOWS = {"publish-release.yml"}
MANUAL_DEBUG_APK_WORKFLOW = "android.yml"
AUTHORIZED_DEBUG_BRANCH = "INFINITY-PACK-14-REMOTE-FRAME-PIPELINE"


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



def _is_safe_manual_debug_apk_workflow(path: Path, content: str) -> bool:
    """Allow only the explicitly authorized, manual, debug-only PR #87 APK workflow."""
    if path.name != MANUAL_DEBUG_APK_WORKFLOW:
        return False
    required = (
        re.search(r"(?m)^on:\s*\n\s+workflow_dispatch:\s*$", content),
        re.search(
            r"(?m)^    if:\s*\$\{\{\s*github\.event_name\s*==\s*'workflow_dispatch'\s*&&\s*github\.ref\s*==\s*'refs/heads/"
            + re.escape(AUTHORIZED_DEBUG_BRANCH)
            + r"'\s*\}\}\s*$",
            content,
        ),
        re.search(r"(?m)^permissions:\s*\n\s+contents:\s*read\s*$", content),
        re.search(r"(?m)^\s+ref:\s*" + re.escape(AUTHORIZED_DEBUG_BRANCH) + r"\s*$", content),
        re.search(r"(?m)^\s+gradle -PINFINITY_BUILD_FREERDP_NATIVE=true :freerdpCore:externalNativeBuildDebug compileDebugKotlin testDebugUnitTest lintDebug assembleDebug --stacktrace\s*$", content),
        re.search(r"(?m)^\s*\"?\$\{APKSIGNER\}\"? verify --verbose --print-certs", content),
        re.search(r'(?m)^\s+APK="app/build/outputs/apk/debug/app-debug\.apk"\s*$', content),
        re.search(r"(?m)^\s+REQUIRED_FREERDP_LIBRARIES=\($", content),
        re.search(r"(?m)^\s+libfreerdp-android\.so$", content),
        re.search(r"(?m)^\s+libfreerdp3\.so$", content),
        re.search(r"(?m)^\s+libfreerdp-client3\.so$", content),
        re.search(r"(?m)^\s+libwinpr3\.so$", content),
        re.search(r"(?m)^\s+libssl\.so$", content),
        re.search(r"(?m)^\s+libcrypto\.so$", content),
        re.search(r"(?m)^\s+liburiparser\.so$", content),
        re.search(r"unzip -Z1", content),
        re.search(r"grep -Fxq", content),
        re.search(r"ndk;27\.0\.12077973", content),
        re.search(r"cmake;3\.22\.1", content),
        re.search(r'entry="lib/arm64-v8a/\$\{library\}"', content),
        re.search(r"unzip -p", content),
        re.search(r"readelf -h", content),
        re.search(r"ELF64", content),
        re.search(r"AArch64", content),
        re.search(r"test -s", content),
    )
    if not all(required):
        return False

    forbidden = (
        r"(?i)assembleRelease|bundleRelease|packageRelease|publish\w*|softprops/action-gh-release|"
        r"gh\s+release|contents:\s*write|packages:\s*write|"
        r"INFINITY_KEYSTORE_BASE64|INFINITY_KEYSTORE_PASSWORD|INFINITY_KEY_ALIAS|INFINITY_KEY_PASSWORD|"
        r"signingConfig|signingConfigs\.release"
    )
    if re.search(forbidden, content) or "secrets." in content:
        return False

    # The DEBUG APK is inspected in-place but never uploaded or published.
    artifact_blocks = _artifact_steps(content)
    if len(artifact_blocks) != 1:
        return False
    evidence_block = artifact_blocks[0]
    return bool(
        "app/build/outputs/apk/debug/app-debug.apk" not in evidence_block
        and ".apk" not in evidence_block.lower()
        and "app/build/outputs" not in evidence_block
        and re.search(r"(?m)^\s+name:\s*PROJECT-INFINITY-0\.10-DEBUG-APK-EVIDENCE\s*$", evidence_block)
        and "app-debug-signature.txt" in evidence_block
        and "app-debug-size.txt" in evidence_block
        and "app-debug-freerdp-libraries.txt" in evidence_block
    )



FASE2_DEBUG_APK_WORKFLOW = "android-fase2-test-apk.yml"


def _is_safe_fase2_debug_apk_workflow(path: Path, content: str) -> bool:
    """Strict exception for the branch-pinned, DEBUG-only Fase II acceptance APK."""
    if path.name != FASE2_DEBUG_APK_WORKFLOW:
        return False
    required = [
        "on:\n  workflow_dispatch:",
        "contents: read",
        "github.event_name == 'workflow_dispatch'",
        "github.ref == 'refs/heads/INFINITY-BASE-PACKAGES-01-05'",
        "ref: ${{ github.sha }}",
        'test "$(git rev-parse HEAD)" = "${GITHUB_SHA}"',
        "gradle -PINFINITY_BUILD_FREERDP_NATIVE=true :freerdpCore:externalNativeBuildDebug compileDebugKotlin testDebugUnitTest lintDebug assembleDebug --stacktrace",
        'APK="app/build/outputs/apk/debug/app-debug.apk"',
        "REQUIRED_FREERDP_LIBRARIES=(",
        "libfreerdp-android.so", "libfreerdp3.so", "libfreerdp-client3.so",
        "libwinpr3.so", "libssl.so", "libcrypto.so", "liburiparser.so",
        "CN=Android Debug", "readelf -h", "ELF64", "AArch64",
        "ndk;27.0.12077973", "cmake;3.22.1",
    ]
    if not all(item in content for item in required):
        return False
    forbidden = (
        "on:\n  push:", "github.event_name == 'push'", "assembleRelease", "bundleRelease", "publish",
        "softprops/action-gh-release", "contents: write", "packages: write",
        "INFINITY_KEYSTORE_BASE64", "INFINITY_KEYSTORE_PASSWORD",
        "INFINITY_KEY_ALIAS", "INFINITY_KEY_PASSWORD", "signingConfig", "secrets."
    )
    if any(item in content for item in forbidden):
        return False
    jobs = re.search(r"(?ms)^jobs:\s*\n(.*)\Z", content)
    if not jobs or len(re.findall(r"(?m)^  [A-Za-z0-9_-]+:\s*$", jobs.group(1))) != 1:
        return False
    blocks = _artifact_steps(content)
    if len(blocks) != 1:
        return False
    block = blocks[0]
    if "name: PROJECT-INFINITY-FASE-II-DEBUG-TEST-APK" not in block or "retention-days: 7" not in block:
        return False
    path_match = re.search(r"(?ims)^\s*path\s*:\s*\|\s*\n(.*?)(?=^\s*[a-zA-Z_-]+\s*:|\Z)", block)
    if not path_match:
        return False
    entries = [line.strip() for line in path_match.group(1).splitlines() if line.strip()]
    expected = {
        "app/build/outputs/apk/debug/app-debug.apk", "app-debug-sha256.txt",
        "app-debug-size.txt", "app-debug-signature.txt", "app-debug-freerdp-libraries.txt"
    }
    return set(entries) == expected and len(entries) == len(expected)


def _audit_file(workflow: Path) -> list[str]:
    try:
        content = workflow.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{workflow}: cannot read workflow: {exc}"]

    # Release remains disabled; the sole APK exception is a manually dispatched,
    # branch-pinned DEBUG-only workflow with no commercial signing or publication.
    disabled_apk_workflow = _is_temporarily_disabled_apk_workflow(workflow, content)
    safe_debug_workflow = _is_safe_manual_debug_apk_workflow(workflow, content)
    safe_fase2_debug_workflow = _is_safe_fase2_debug_apk_workflow(workflow, content)
    if workflow.name == MANUAL_DEBUG_APK_WORKFLOW and not safe_debug_workflow:
        return [f"{workflow}: manual DEBUG APK workflow does not satisfy the strict PR #87 allowlist"]

    if workflow.name == FASE2_DEBUG_APK_WORKFLOW and not safe_fase2_debug_workflow:
        return [f"{workflow}: Fase II DEBUG APK workflow does not satisfy the strict branch-pinned test allowlist"]

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
    if (gradle_build or has_apk_output) and not (disabled_apk_workflow or safe_debug_workflow or safe_fase2_debug_workflow):
        findings.append(f"{workflow}: APK build, signing, or publication route is not disabled")

    if not (disabled_apk_workflow or safe_debug_workflow or safe_fase2_debug_workflow):
        for block in _artifact_steps(content):
            path_match = re.search(
                r"(?ims)^\s*path\s*:\s*(.*?)(?=^\s*[a-zA-Z_-]+\s*:|\Z)",
                block,
            )
            name_match = re.search(r"(?im)^\s*name\s*:\s*(.*?)\s*$", block)
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
    print("Workflow policy passed: only the branch-pinned manual DEBUG APK allowlist is enabled; Release/publication and all other APK routes remain blocked")
