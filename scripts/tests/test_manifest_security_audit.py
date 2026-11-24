import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_android_security_manifest import audit_manifest

NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'

def write_manifest(body):
    handle = tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False)
    handle.write(f'<manifest {NS}><application {body}></application></manifest>')
    handle.close()
    return handle.name

class ManifestSecurityAuditTest(unittest.TestCase):
    def test_accepts_protected_notification_listener_and_launcher(self):
        path = write_manifest('android:allowBackup="false" android:usesCleartextTraffic="false"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity><service android:name=".Listener" android:exported="true" android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"/>')
        self.assertEqual([], audit_manifest(path))

    def test_rejects_cleartext_and_backup(self):
        path = write_manifest('android:allowBackup="true" android:usesCleartextTraffic="true"')
        findings = audit_manifest(path)
        self.assertTrue(any("allowBackup=false" in item for item in findings))
        self.assertTrue(any("usesCleartextTraffic=false" in item for item in findings))

    def test_rejects_unprotected_exported_component(self):
        path = write_manifest('android:allowBackup="false" android:usesCleartextTraffic="false"><receiver android:name=".OpenReceiver" android:exported="true"/>')
        self.assertTrue(any("OpenReceiver" in item for item in audit_manifest(path)))

    def test_requires_explicit_exported_attribute(self):
        path = write_manifest('android:allowBackup="false" android:usesCleartextTraffic="false"><service android:name=".Listener"/>')
        self.assertTrue(any("explicitly declare" in item for item in audit_manifest(path)))

    def test_rejects_debuggable_release_manifest(self):
        path = write_manifest('android:allowBackup="false" android:usesCleartextTraffic="false" android:debuggable="true"')
        self.assertTrue(any("debuggable" in item for item in audit_manifest(path)))

if __name__ == "__main__":
    unittest.main()
