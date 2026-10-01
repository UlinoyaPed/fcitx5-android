#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SCRIPT_UNDER_TEST="${REPO_ROOT}/prepare_personal_build.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/prepare-personal-build-test.XXXXXX")"
trap 'rm -rf "${TMP_ROOT}"' EXIT

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

assert_eq() {
    local expected="$1"
    local actual="$2"
    local message="$3"
    [[ "${expected}" == "${actual}" ]] || fail "${message}: expected '${expected}', got '${actual}'"
}

init_repo() {
    local repository="$1"
    git init -q "${repository}"
    git -C "${repository}" config user.name test
    git -C "${repository}" config user.email test@example.invalid
}

make_remote_with_drift() {
    local name="$1"
    local remote="${TMP_ROOT}/${name}.git"
    local work="${TMP_ROOT}/${name}-work"

    git init --bare -q "${remote}"
    init_repo "${work}"
    printf 'pinned-%s\n' "${name}" > "${work}/state.txt"
    git -C "${work}" add state.txt
    git -C "${work}" commit -q -m pinned
    local pinned
    pinned="$(git -C "${work}" rev-parse HEAD)"
    git -C "${work}" branch -M master
    git -C "${work}" -c protocol.file.allow=always push -q "${remote}" master
    git -C "${remote}" symbolic-ref HEAD refs/heads/master

    printf 'remote-master-drift-%s\n' "${name}" > "${work}/state.txt"
    git -C "${work}" commit -qam drift
    local drift
    drift="$(git -C "${work}" rev-parse HEAD)"
    git -C "${work}" -c protocol.file.allow=always push -q "${remote}" master

    printf '%s\n%s\n%s\n' "${remote}" "${pinned}" "${drift}"
}

# Build three local repositories and a superproject whose gitlinks point at the
# first commit while both remote master branches have already drifted.
readarray -t RIME_INFO < <(make_remote_with_drift rime-remote)
readarray -t PREBUILT_INFO < <(make_remote_with_drift prebuilt-remote)
RIME_REMOTE="${RIME_INFO[0]}"
RIME_PIN="${RIME_INFO[1]}"
RIME_DRIFT="${RIME_INFO[2]}"
PREBUILT_REMOTE="${PREBUILT_INFO[0]}"
PREBUILT_PIN="${PREBUILT_INFO[1]}"
PREBUILT_DRIFT="${PREBUILT_INFO[2]}"
assert_eq "${RIME_DRIFT}" "$(git --git-dir "${RIME_REMOTE}" rev-parse refs/heads/master)" "Rime remote master drift"
assert_eq "${PREBUILT_DRIFT}" "$(git --git-dir "${PREBUILT_REMOTE}" rev-parse refs/heads/master)" "prebuilt remote master drift"

FCITX_REMOTE="${TMP_ROOT}/fcitx5.git"
FCITX_WORK="${TMP_ROOT}/fcitx5-work"
git init --bare -q "${FCITX_REMOTE}"
init_repo "${FCITX_WORK}"
printf 'base-global\n' > "${FCITX_WORK}/global.txt"
printf 'base-insert-space\n' > "${FCITX_WORK}/insert-space.txt"
git -C "${FCITX_WORK}" add global.txt insert-space.txt
git -C "${FCITX_WORK}" commit -q -m base
FCITX_PIN="$(git -C "${FCITX_WORK}" rev-parse HEAD)"
git -C "${FCITX_WORK}" branch -M master
git -C "${FCITX_WORK}" -c protocol.file.allow=always push -q "${FCITX_REMOTE}" master
    git -C "${FCITX_REMOTE}" symbolic-ref HEAD refs/heads/master

PATCH_ROOT="${TMP_ROOT}/patches"
mkdir -p "${PATCH_ROOT}"
printf 'global\n' > "${FCITX_WORK}/global.txt"
git -C "${FCITX_WORK}" diff -- global.txt > "${PATCH_ROOT}/global.patch"
printf 'insert-space\n' > "${FCITX_WORK}/insert-space.txt"
git -C "${FCITX_WORK}" diff -- insert-space.txt > "${PATCH_ROOT}/insert-space.patch"
git -C "${FCITX_WORK}" reset -q -- global.txt insert-space.txt
git -C "${FCITX_WORK}" checkout -q -- global.txt insert-space.txt

FIXTURE="${TMP_ROOT}/fixture"
mkdir -p "${FIXTURE}/plugin/rime/src/main/cpp" "${FIXTURE}/lib/fcitx5/src/main/cpp"
git init -q "${FIXTURE}"
git -C "${FIXTURE}" config user.name test
git -C "${FIXTURE}" config user.email test@example.invalid

git -C "${FIXTURE}" -c protocol.file.allow=always submodule add -q "${FCITX_REMOTE}" lib/fcitx5/src/main/cpp/fcitx5
git -C "${FIXTURE}/lib/fcitx5/src/main/cpp/fcitx5" checkout -q --detach "${FCITX_PIN}"
git -C "${FIXTURE}" -c protocol.file.allow=always submodule add -q "${RIME_REMOTE}" plugin/rime/src/main/cpp/fcitx5-rime
git -C "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime" checkout -q --detach "${RIME_PIN}"
git -C "${FIXTURE}" -c protocol.file.allow=always submodule add -q "${PREBUILT_REMOTE}" lib/fcitx5/src/main/cpp/prebuilt
git -C "${FIXTURE}/lib/fcitx5/src/main/cpp/prebuilt" checkout -q --detach "${PREBUILT_PIN}"
git -C "${FIXTURE}" add .gitmodules lib/fcitx5/src/main/cpp plugin/rime/src/main/cpp
# The script reads these patch paths from the environment; no real project patch
# is touched by this offline fixture.
git -C "${FIXTURE}" commit -q -m fixture

run_script() {
    PREPARE_PERSONAL_BUILD_PROJECT_ROOT="${FIXTURE}" \
    FCITX5_RIME_REPO="file://${RIME_REMOTE}" \
    PREBUILT_REPO="file://${PREBUILT_REMOTE}" \
    PREBUILDER_REPO="file://${TMP_ROOT}/prebuilder.git" \
    FCITX5_GLOBAL_OPTIONS_UI_PATCH="${PATCH_ROOT}/global.patch" \
    FCITX5_INSERT_SPACE_ZH_EN_PATCH="${PATCH_ROOT}/insert-space.patch" \
    bash "${SCRIPT_UNDER_TEST}"
}

run_script >/dev/null 2>&1
assert_eq "${RIME_PIN}" "$(git -C "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime" rev-parse HEAD)" "Rime gitlink pin"
assert_eq "${PREBUILT_PIN}" "$(git -C "${FIXTURE}/lib/fcitx5/src/main/cpp/prebuilt" rev-parse HEAD)" "prebuilt gitlink pin"
assert_eq "${FCITX_PIN}" "$(git -C "${FIXTURE}/lib/fcitx5/src/main/cpp/fcitx5" rev-parse HEAD)" "Fcitx5 base pin"
assert_eq 'global' "$(cat "${FIXTURE}/lib/fcitx5/src/main/cpp/fcitx5/global.txt")" "global patch sequence"
assert_eq 'insert-space' "$(cat "${FIXTURE}/lib/fcitx5/src/main/cpp/fcitx5/insert-space.txt")" "insert-space patch sequence"
assert_eq "file://${RIME_REMOTE}" "$(git -C "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime" config remote.gh.url)" "Rime remote config"
assert_eq "file://${PREBUILT_REMOTE}" "$(git -C "${FIXTURE}/lib/fcitx5/src/main/cpp/prebuilt" config remote.gh.url)" "prebuilt remote config"

# A second invocation must recognize both patches as already applied and keep
# the pinned submodules unchanged.
run_script >/dev/null 2>&1
assert_eq "${RIME_PIN}" "$(git -C "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime" rev-parse HEAD)" "repeat Rime pin"
assert_eq "${PREBUILT_PIN}" "$(git -C "${FIXTURE}/lib/fcitx5/src/main/cpp/prebuilt" rev-parse HEAD)" "repeat prebuilt pin"
assert_eq 'global' "$(cat "${FIXTURE}/lib/fcitx5/src/main/cpp/fcitx5/global.txt")" "repeat global patch"
assert_eq 'insert-space' "$(cat "${FIXTURE}/lib/fcitx5/src/main/cpp/fcitx5/insert-space.txt")" "repeat insert-space patch"

# A mismatched submodule with local changes must fail without discarding them.
git -C "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime" checkout -q --detach "${RIME_DRIFT}"
printf 'local-change\n' >> "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime/state.txt"
if run_script >"${TMP_ROOT}/dirty.log" 2>&1; then
    fail 'dirty Rime submodule was silently moved'
fi
assert_eq "${RIME_DRIFT}" "$(git -C "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime" rev-parse HEAD)" "dirty Rime HEAD preserved"
grep -q '^local-change$' "${FIXTURE}/plugin/rime/src/main/cpp/fcitx5-rime/state.txt" || fail 'dirty Rime content was lost'
grep -q 'refusing to move it' "${TMP_ROOT}/dirty.log" || fail 'dirty checkout refusal was not reported'

# Confirm the production defaults remain the boomker fork/configuration.
grep -Fq 'https://github.com/boomker/fcitx5-rime.git' "${SCRIPT_UNDER_TEST}" || fail 'boomker Rime default changed'
grep -Fq 'https://github.com/boomker/f5a-prebuilt.git' "${SCRIPT_UNDER_TEST}" || fail 'boomker prebuilt default changed'
grep -Fq 'https://github.com/boomker/f5a-prebuilder.git' "${SCRIPT_UNDER_TEST}" || fail 'boomker prebuilder default changed'
! grep -Fq 'gh/master' "${SCRIPT_UNDER_TEST}" || fail 'moving gh/master checkout remains'
! grep -Eq 'checkout (-f|--force)|checkout -- \.' "${SCRIPT_UNDER_TEST}" || fail 'destructive checkout remains'

echo 'PASS: pinned submodules survive remote master drift, repeat safely, and refuse dirty moves'
