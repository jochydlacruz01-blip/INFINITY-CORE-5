#!/usr/bin/env python3
"""Final integration inventory gate for the INFINITY 0.9 package stack."""
from pathlib import Path
import sys

REQUIRED = [
    "app/src/main/java/com/infinity/core/InfinityFilesTransferPolicy.kt",
    "app/src/main/java/com/infinity/core/InfinityWorkspaceIconPosition.kt",
    "app/src/main/java/com/infinity/core/InfinityDockResponsivePolicy.kt",
    "app/src/main/java/com/infinity/core/InfinityStartCatalogPolicy.kt",
    "app/src/main/java/com/infinity/core/InfinityAuroraWindowStack.kt",
    "app/src/main/java/com/infinity/core/InfinityConnectPreflight.kt",
    "app/src/main/java/com/infinity/core/InfinityControlCenterPolicy.kt",
    "app/src/main/java/com/infinity/core/InfinityOrbitWorkspacePresentation.kt",
    "app/src/test/java/com/infinity/core/InfinityFilesTransferPolicyTest.kt",
    "app/src/test/java/com/infinity/core/InfinityWorkspaceIconPositionTest.kt",
    "app/src/test/java/com/infinity/core/InfinityDockResponsivePolicyTest.kt",
    "app/src/test/java/com/infinity/core/InfinityStartCatalogPolicyTest.kt",
    "app/src/test/java/com/infinity/core/InfinityAuroraWindowStackTest.kt",
    "app/src/test/java/com/infinity/core/InfinityConnectPreflightTest.kt",
    "app/src/test/java/com/infinity/core/InfinityControlCenterPolicyTest.kt",
    "app/src/test/java/com/infinity/core/InfinityOrbitWorkspacePresentationTest.kt",
    "app/src/main/java/com/infinity/core/InfinityRemoteFramePipeline.kt",
    "app/src/test/java/com/infinity/core/InfinityRemoteFrameMailboxTest.kt",
    "scripts/verify_android_security_manifest.py",
    "scripts/tests/test_manifest_security_audit.py",
    "docs/INFINITY-SECURITY-MANIFEST-AUDIT.md",
]


def missing_inventory(root: Path) -> list[str]:
    return [item for item in REQUIRED if not (root / item).is_file()]


if __name__ == "__main__":
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    missing = missing_inventory(root)
    if missing:
        print("Missing integration deliverables:")
        print("\n".join(" - " + item for item in missing))
        raise SystemExit(1)
    print(f"INFINITY package inventory passed ({len(REQUIRED)} required files)")
