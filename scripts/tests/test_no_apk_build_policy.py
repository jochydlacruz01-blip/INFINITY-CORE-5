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


def audit_named_workflow(name: str, content: str) -> list[str]:
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / name
        path.write_text(content, encoding="utf-8")
        return audit_workflow(path)


class NoApkBuildPolicyTest(unittest.TestCase):
    def test_source_only_workflow_is_accepted(self):
        self.assertEqual(audit_text("steps:\n  - run: gradle compileDebugKotlin testDebugUnitTest lintDebug\n"), [])

    def test_debug_and_release_apk_tasks_are_rejected(self):
        self.assertTrue(audit_text("run: gradle assembleDebug"))
        self.assertTrue(audit_text("run: gradle assembleRelease"))

    def test_gradle_wrapper_apk_tasks_are_rejected(self):
        self.assertTrue(audit_text("run: ./gradlew assembleDebug"))
        self.assertTrue(audit_text("run: gradlew.bat bundleRelease"))

    def test_gradle_wrapper_continuation_cannot_hide_apk_task(self):
        content = "run: ./gradlew \\\n  assembleDebug"
        self.assertTrue(audit_text(content))

    def test_apk_output_variable_reference_is_rejected(self):
        self.assertTrue(audit_text('run: cp "$APK_OUTPUT_PATH" ./dist/'))

    def test_install_and_build_tasks_are_rejected(self):
        self.assertTrue(audit_text("run: gradle :app:installDebug"))
        self.assertTrue(audit_text("run: gradle build"))

    def test_package_and_bundle_tasks_are_rejected(self):
        self.assertTrue(audit_text("run: gradle packageDebug"))
        self.assertTrue(audit_text("run: gradle bundleRelease"))

    def test_signing_configuration_is_rejected(self):
        self.assertTrue(audit_text("signingConfig = signingConfigs.release"))

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

    def test_indirect_generic_output_directory_is_rejected(self):
        content = """steps:
  - name: Upload
    uses: actions/upload-artifact@v7
    with:
      name: build
      path: ${{ env.OUTPUT_DIRECTORY }}
"""
        self.assertTrue(audit_text(content))

    def test_release_asset_publication_is_rejected(self):
        content = """steps:
  - name: Publish release assets
    uses: softprops/action-gh-release@v3
    with:
      files: app-release.apk
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

    def test_android_and_freerdp_lint_html_reports_are_allowed(self):
        content = """steps:
  - uses: actions/upload-artifact@v7
    with:
      name: lint
      path: |
        app/build/reports/lint-results*.html
        freerdp-core/build/reports/lint-results*.html
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

    def test_windows_companion_non_apk_artifact_is_allowed(self):
        content = """steps:
  - uses: actions/upload-artifact@v7
    with:
      name: INFINITY-WINDOWS-COMPANION-win-x64
      path: artifacts/infinity-windows-companion/
"""
        self.assertEqual(audit_text(content), [])

    def test_codmagic_release_apk_build_route_is_rejected(self):
        content = """workflows:
  infinity-core-android:
    scripts:
      - name: Build APKs
        script: gradle assembleDebug assembleRelease
    artifacts:
      - app/build/outputs/apk/**/*.apk
"""
        self.assertTrue(audit_named_workflow("codemagic.yaml", content))

    def test_codmagic_source_only_pipeline_is_allowed(self):
        content = """workflows:
  infinity-core-android:
    scripts:
      - name: Unit tests
        script: gradle testDebugUnitTest testReleaseUnitTest
      - name: Lint
        script: gradle lintDebug lintRelease
    artifacts:
      - app/build/test-results/**/*.xml
      - app/build/reports/lint-results*.html
"""
        self.assertEqual(audit_named_workflow("codemagic.yaml", content), [])

    def test_manual_apk_workflow_requires_every_job_disabled(self):
        content = """name: legacy release
on:
  workflow_dispatch:
jobs:
  build:
    if: false # temporary release hold
    steps:
      - run: gradle assembleRelease
"""
        self.assertEqual(audit_named_workflow("publish-release.yml", content), [])

    def test_manual_apk_workflow_without_job_gate_is_rejected(self):
        content = """name: legacy release
on:
  workflow_dispatch:
jobs:
  build:
    steps:
      - run: gradle assembleRelease
"""
        self.assertTrue(audit_named_workflow("android.yml", content))

    def test_manual_apk_workflow_with_an_ungated_second_job_is_rejected(self):
        content = """name: legacy release
on:
  workflow_dispatch:
jobs:
  build:
    if: false
    steps:
      - run: gradle assembleRelease
  publish:
    steps:
      - uses: softprops/action-gh-release@v3
"""
        self.assertTrue(audit_named_workflow("publish-release.yml", content))


    def test_authorized_manual_debug_workflow_is_allowed(self):
        self.assertEqual(
            audit_named_workflow("android.yml", self._authorized_debug_workflow_fixture()),
            [],
        )

    def _authorized_debug_workflow_fixture(self):
        return """name: PROJECT INFINITY 0.10 - DEBUG TEST APK
on:
  workflow_dispatch:
permissions:
  contents: read
jobs:
  build-debug-test-apk:
    if: ${{ github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/INFINITY-PACK-14-REMOTE-FRAME-PIPELINE' }}
    steps:
      - uses: actions/checkout@v7
        with:
          ref: INFINITY-PACK-14-REMOTE-FRAME-PIPELINE
      - name: Install Android native build toolchain
        run: sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "ndk;27.0.12077973" "cmake;3.22.1"
      - name: Compile native and DEBUG
        run: |
          gradle -PINFINITY_BUILD_FREERDP_NATIVE=true :freerdpCore:externalNativeBuildDebug compileDebugKotlin testDebugUnitTest lintDebug assembleDebug --stacktrace
      - name: Verify DEBUG APK and native libraries
        run: |
          APK="app/build/outputs/apk/debug/app-debug.apk"
          test -s "${APK}"
          APK_ENTRIES="$(unzip -Z1 "${APK}")"
          REQUIRED_FREERDP_LIBRARIES=(
            libfreerdp-android.so
            libfreerdp3.so
            libfreerdp-client3.so
            libwinpr3.so
            libssl.so
            libcrypto.so
            liburiparser.so
          )
          for library in "${REQUIRED_FREERDP_LIBRARIES[@]}"; do
            entry="lib/arm64-v8a/${library}"
            grep -Fxq "${entry}" <<< "${APK_ENTRIES}"
            extracted="apk-native-inspection/${library}"
            unzip -p "${APK}" "${entry}" > "${extracted}"
            test -s "${extracted}"
            readelf -h "${extracted}"
            echo "ELF64 AArch64"
          done
          "${APKSIGNER}" verify --verbose --print-certs "${APK}"
      - name: Upload DEBUG APK and evidence
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: PROJECT-INFINITY-0.10-DEBUG-APK-TEST
          path: |
            app/build/outputs/apk/debug/app-debug.apk
            app-debug-sha256.txt
            app-debug-size.txt
            app-debug-signature.txt
            app-debug-freerdp-libraries.txt
          if-no-files-found: warn
          retention-days: 3
"""
    def test_manual_debug_workflow_wrong_branch_is_rejected(self):
        content = """on:
  workflow_dispatch:
permissions:
  contents: read
jobs:
  build:
    if: ${{ github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' }}
    steps:
      - run: gradle assembleDebug
"""
        self.assertTrue(audit_named_workflow("android.yml", content))

    def test_manual_debug_workflow_with_commercial_signing_is_rejected(self):
        content = """on:
  workflow_dispatch:
permissions:
  contents: read
jobs:
  build:
    if: ${{ github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/INFINITY-PACK-14-REMOTE-FRAME-PIPELINE' }}
    steps:
      - run: gradle assembleDebug
      - run: echo ${{ secrets.INFINITY_KEYSTORE_PASSWORD }}
"""
        self.assertTrue(audit_named_workflow("android.yml", content))



if __name__ == "__main__":
    unittest.main()
