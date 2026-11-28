#!/bin/sh
set -eu

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
run_instrumented "process-restart" "com.infinity.core.InfinityProcessPersistenceInstrumentedTest#stateSurvivesRealTargetProcessDeathInSingleSession"

adb logcat -d -v threadtime AndroidRuntime:E INFINITY_WINDOW_RECOVERY:W '*:S' > "$evidence/process-persistence-logcat.txt" 2>&1 || true
