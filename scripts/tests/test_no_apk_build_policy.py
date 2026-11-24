import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_no_apk_builds import audit_workflow


def audit_text(content: str) -> list[str]:
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "workflow.yml"
        path.write_text(content, encoding="utf-8")
        return audit_workflow(path)


class NoApkBuildPolicyTest(unittest.TestCase):
    def test_source_only_workflow_is_accepted(self):
        content = """
steps:
  - run: gradle compileDebugKotlin testDebugUnitTest lintDebug
"""
        self.assertEqual(audit_text(content), [])

    def test_debug_apk_task_is_rejected(self):
        self.assertTrue(audit_text("run: gradle assembleDebug"))

    def test_release_apk_task_is_rejected(self):
        self.assertTrue(audit_text("run: gradle assembleRelease"))

    def test_artifact_upload_is_rejected(self):
        self.assertTrue(audit_text("uses: actions/upload-artifact@v4"))

    def test_signing_configuration_is_rejected(self):
        self.assertTrue(audit_text("signingConfig = signingConfigs.release"))


if __name__ == "__main__":
    unittest.main()
