#!/usr/bin/env bash
# Manual validation only. Never publishes packages or installable releases.
set -euo pipefail
cd "$(dirname "$0")/.."
results=maintenance/validation-results
mkdir -p "$results"
git rev-parse HEAD > "$results/source-sha.txt"
local_repository="file://${PWD}/.local-thirdparty-artifacts"
common=(--console=plain --max-workers=2 -Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process -PgithubPackagesRepository="$local_repository")
run_gradle() {
    local name="$1"; shift
    ./gradlew "${common[@]}" "$@" 2>&1 | tee "$results/$name.log"
}
python3 -m unittest discover -s third_party/onnxruntime-genai-android/tests -v 2>&1 | tee "$results/python-tests.log"
run_gradle dependency --no-configuration-cache :third_party:onnxruntime-genai-android:publish
BUILD_ABI=x86_64 run_gradle unit-tests :app:testDebugUnitTest
python3 maintenance/verify-validation-apks.py --jvm-only | tee "$results/jvm-summary.log"
BUILD_ABI=x86_64 run_gradle android-fixtures :app:assembleDebug :app:assembleDebugAndroidTest :plugin:rime:assembleDebug :plugin:anthy:assembleDebug
BUILD_ABI=arm64-v8a run_gradle release-apks :app:assembleRelease :assembleReleasePlugins
# Retain lint failures but still exercise Android paths; the runner checks this status.
set +e
BUILD_ABI=arm64-v8a run_gradle lint :app:lintDebug :app:lintRelease
lint_status=$?
set -e
printf '%s\n' "$lint_status" > "$results/lint-exit-code.txt"
python3 maintenance/verify-validation-apks.py
./gradlew --stop
