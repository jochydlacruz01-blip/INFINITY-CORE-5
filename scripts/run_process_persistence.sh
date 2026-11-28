#!/bin/sh
set -eu

package="com.infinity.core"
test_class="com.infinity.core.InfinityProcessPersistenceInstrumentedTest"
evidence="artifacts/test-results"
mkdir -p "$evidence"

run_instrumented() {
  label="$1"
  class_filter="$2"
  log_file="$evidence/$label-instrumentation.log"
  if ! gradle connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$class_filter" --stacktrace > "$log_file" 2>&1; then
    cat "$log_file"
    return 1
  fi
  cat "$log_file"
  # A successful Gradle task that discovers zero tests is not evidence.
  if ! grep -Eq 'Starting [1-9][0-9]* tests? on ' "$log_file"; then
    echo "ERROR: $label did not execute any Android instrumentation tests." | tee -a "$log_file"
    return 1
  fi
  if grep -Eq 'Starting 0 tests? on ' "$log_file"; then
    echo "ERROR: $label reported zero Android instrumentation tests." | tee -a "$log_file"
    return 1
  fi
}

run_instrumented "window-recovery" "com.infinity.core.InfinityWindowSnapshotRecoveryInstrumentedTest"
run_instrumented "seed" "$test_class#seedThreeWorkspaceStateBeforeHostForceStop"

# The capture instrumentation test launches the registered launcher Activity itself.
# Avoid a second, hard-coded component launch racing package/activity resolution.
run_instrumented "capture" "$test_class#captureStateAfterInitialAppLaunch"

before_pid=""
attempt=0
while [ "$attempt" -lt 60 ]; do
  before_pid="$(adb shell pidof "$package" 2>/dev/null | tr -d '\r' || true)"
  [ -n "$before_pid" ] && break
  attempt=$((attempt + 1))
  sleep 1
done
if [ -z "$before_pid" ]; then
  echo "ERROR: could not observe the original INFINITY process." >&2
  exit 1
fi
printf 'Original app PID: %s\n' "$before_pid" | tee "$evidence/process-lifecycle.txt"

adb shell am force-stop "$package"
stopped=0
attempt=0
while [ "$attempt" -lt 30 ]; do
  current_pid="$(adb shell pidof "$package" 2>/dev/null | tr -d '\r' || true)"
  if [ -z "$current_pid" ]; then stopped=1; break; fi
  attempt=$((attempt + 1))
  sleep 1
done
if [ "$stopped" -ne 1 ]; then
  echo "ERROR: ActivityManager did not stop the original process." >&2
  exit 1
fi
echo "ActivityManager confirmed the original app process stopped." | tee -a "$evidence/process-lifecycle.txt"

# The verification instrumentation test launches the launcher Activity itself.
after_pid=""
attempt=0
while [ "$attempt" -lt 60 ]; do
  after_pid="$(adb shell pidof "$package" 2>/dev/null | tr -d '\r' || true)"
  [ -n "$after_pid" ] && break
  attempt=$((attempt + 1))
  sleep 1
done
if [ -z "$after_pid" ] || [ "$before_pid" = "$after_pid" ]; then
  echo "ERROR: relaunch did not produce a new application PID." >&2
  exit 1
fi
printf 'Fresh app PID: %s\n' "$after_pid" | tee -a "$evidence/process-lifecycle.txt"

run_instrumented "verify" "$test_class#verifyStateAfterHostPerformedProcessRestart"
adb logcat -d -v threadtime AndroidRuntime:E INFINITY_WINDOW_RECOVERY:W '*:S' > "$evidence/process-persistence-logcat.txt" 2>&1 || true
