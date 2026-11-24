import tempfile
import unittest
from pathlib import Path

from verify_no_apk_builds import audit_workflow


class NoApkBuildPolicyTest(unittest.TestCase):
    def audit_text(self, content: str) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "workflow.yml"
            path.write_text(content, encoding="utf-8")
            return audit_workflow(path)

    def test_source_only_workflow_is_accepted(self):
        content = """
steps:
  - run: gradle compileDebugKotlin testDebugUnitTest lintDebug
"""
        self.assertEqual(audit_workflow_from_text(self, content), [])

    def test_debug_apk_task_is_rejected(self):
        self.assertTrue(self.audit_text("run: gradle assembleDebug"))

    def test_release_apk_task_is_rejected(self):
        self.assertTrue(self.audit_text("run: gradle assembleRelease"))

    def test_artifact_upload_is_rejected(self):
        self.assertTrue(self.audit_text("uses: actions/upload-artifact@v4"))

    def test_signing_configuration_is_rejected(self):
        self.assertTrue(self.audit_text("signingConfig = signingConfigs.release"))


def audit_workflow_from_text(test_case, content: str) -> list[str]:
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "workflow.yml"
        path.write_text(content, encoding="utf-8")
        return audit_workflow(path)


if __name__ == "__main__":
    unittest.main()
