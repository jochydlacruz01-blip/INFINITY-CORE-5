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

    def test_package_and_bundle_tasks_are_rejected(self):
        self.assertTrue(audit_text("run: gradle packageDebug"))
        self.assertTrue(audit_text("run: gradle bundleRelease"))

    def test_signing_configuration_is_rejected(self):
        self.assertTrue(audit_text("signingConfig = signingConfigs.release"))

    def test_gradle_wrapper_apk_tasks_are_rejected(self):
        self.assertTrue(audit_text("run: ./gradlew assembleDebug"))
        self.assertTrue(audit_text("run: gradlew.bat bundleRelease"))

    def test_install_and_build_tasks_are_rejected(self):
        self.assertTrue(audit_text("run: gradle :app:installDebug"))
        self.assertTrue(audit_text("run: gradle build"))

    def test_release_asset_publication_is_rejected(self):
        content = """steps:
  - name: Publish release assets
    uses: softprops/action-gh-release@v3
    with:
      files: app-release.apk
"""
        self.assertTrue(audit_text(content))

    def test_indirect_generic_output_directory_is_rejected(self):
        content = """steps:
  - name: Upload
    uses: actions/upload-artifact@v7
    with:
      name: build
      path: ${{ env.OUTPUT_DIRECTORY }}
"""
        self.assertTrue(audit_text(content))

    def test_apk_artifact_name_and_path_are_rejected(self):
        content = """steps:
  - name: Upload
    uses: actions/upload-artifact@v7
    with:
      name: app-debug.apk
      path: app/build/outputs/apk/debug/app-debug.apk
"""
        self.assertTrue(audit_text(content))

    def test_generic_outputs_directory_is_rejected(self):
        content = """steps:
  - name: Upload
    uses: actions/upload-artifact@v7
    with:
      name: build-files
      path: app/build/outputs/**
"""
        self.assertTrue(audit_text(content))

    def test_wildcard_apk_path_is_rejected(self):
        content = """steps:
  - name: Upload
    uses: actions/upload-artifact@v7
    with:
      name: binaries
      path: **/*.apk
"""
        self.assertTrue(audit_text(content))

    def test_indirect_apk_output_variable_is_rejected(self):
        content = """steps:
  - name: Upload
    uses: actions/upload-artifact@v7
    with:
      name: reports
      path: ${{ env.APK_OUTPUT_PATH }}
"""
        self.assertTrue(audit_text(content))

    def test_dynamic_artifact_path_is_rejected_closed(self):
        content = """steps:
  - name: Upload
    uses: actions/upload-artifact@v7
    with:
      name: build
      path: ${{ env.ARTIFACT_PATH }}
"""
        self.assertTrue(audit_text(content))

    def test_xml_html_test_reports_are_allowed(self):
        content = """steps:
  - name: Upload reports
    if: always()
    uses: actions/upload-artifact@v7
    with:
      name: ANDROID-TEST-REPORTS-${{ github.sha }}
      path: |
        app/build/test-results/**
        app/build/reports/tests/**
      if-no-files-found: warn
      retention-days: 14
"""
        self.assertEqual(audit_text(content), [])

    def test_single_xml_report_is_allowed(self):
        content = """steps:
  - uses: actions/upload-artifact@v7
    with:
      name: junit
      path: app/build/test-results/testDebugUnitTest/TEST-result.xml
"""
        self.assertEqual(audit_text(content), [])


if __name__ == "__main__":
    unittest.main()
