#!/usr/bin/bash

set -e  # Exit on error

FCITX5_RIME_REPO="${FCITX5_RIME_REPO:-https://github.com/boomker/fcitx5-rime.git}"
PREBUILT_REPO="${PREBUILT_REPO:-https://github.com/boomker/f5a-prebuilt.git}"
PREBUILDER_REPO="${PREBUILDER_REPO:-https://github.com/boomker/f5a-prebuilder.git}"

# Get script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PREPARE_PERSONAL_BUILD_PROJECT_ROOT:-${SCRIPT_DIR}}"

# Define all paths relative to project root
RIME_DIR="${PROJECT_ROOT}/plugin/rime/src/main/cpp/fcitx5-rime"
FCITX5_DIR="${PROJECT_ROOT}/lib/fcitx5/src/main/cpp/fcitx5"
PREBUILT_DIR="${PROJECT_ROOT}/lib/fcitx5/src/main/cpp/prebuilt"

# Patch files
FCITX5_GLOBAL_OPTIONS_UI_PATCH="${FCITX5_GLOBAL_OPTIONS_UI_PATCH:-${PROJECT_ROOT}/lib/fcitx5/fcitx5-global-options-ui.patch}"
FCITX5_INSERT_SPACE_ZH_EN_PATCH="${FCITX5_INSERT_SPACE_ZH_EN_PATCH:-${PROJECT_ROOT}/lib/fcitx5/fcitx5-insert-space-zh-en.patch}"
# RIME_PREEDIT_LABEL_PATCH="${PROJECT_ROOT}/plugin/rime/fcitx5-rime-preedit-cursor-label.patch"

apply_patch() {
    local repository="$1"
    local patch_file="$2"
    local patch_name="$3"

    if git -C "${repository}" apply --check --ignore-whitespace "${patch_file}"; then
        git -C "${repository}" apply --ignore-whitespace "${patch_file}"
        echo "✓ ${patch_name} patch applied successfully"
    elif git -C "${repository}" apply --reverse --check --ignore-whitespace "${patch_file}"; then
        echo "✓ ${patch_name} patch already applied"
    else
        echo "✗ ${patch_name} patch does not apply" >&2
        return 1
    fi
}

checkout_pinned_submodule() {
    local repository="$1"
    local remote_url="$2"
    local commit="$3"
    local name="$4"

    git -C "${repository}" remote add boomker "${remote_url}" 2>/dev/null || \
        git -C "${repository}" remote set-url boomker "${remote_url}"

    local actual
    actual="$(git -C "${repository}" rev-parse HEAD)"
    if [[ "${actual}" == "${commit}" ]]; then
        echo "✓ ${name} already pinned to ${commit}"
        return 0
    fi

    if [[ -n "$(git -C "${repository}" status --porcelain)" ]]; then
        echo "✗ ${name} has local changes; refusing to move it from ${actual} to ${commit}" >&2
        return 1
    fi

    if ! git -C "${repository}" cat-file -e "${commit}^{commit}" 2>/dev/null; then
        git -C "${repository}" fetch -v boomker "${commit}"
    fi
    git -C "${repository}" checkout --detach "${commit}"
    echo "✓ ${name} pinned to ${commit}"
}

# Keep fcitx5-rime reproducible using the gitlink recorded by this superproject.
# Prebuilt libraries follow the producer's published master branch below.
# Prefer the index so the script also follows a freshly staged gitlink update
# before the containing superproject commit is created.
gitlink_commit() {
    local path="$1"
    git -C "${PROJECT_ROOT}" rev-parse ":${path}" 2>/dev/null || \
        git -C "${PROJECT_ROOT}" rev-parse "HEAD:${path}"
}

RIME_COMMIT="$(gitlink_commit plugin/rime/src/main/cpp/fcitx5-rime)"
checkout_pinned_submodule "${RIME_DIR}" "${FCITX5_RIME_REPO}" "${RIME_COMMIT}" "fcitx5-rime"
# apply_patch "${RIME_DIR}" "${RIME_PREEDIT_LABEL_PATCH}" "preedit cursor label"

# apply fcitx5 patches
echo "applying fcitx5 patches"
apply_patch "${FCITX5_DIR}" "${FCITX5_GLOBAL_OPTIONS_UI_PATCH}" "global-options-ui"
apply_patch "${FCITX5_DIR}" "${FCITX5_INSERT_SPACE_ZH_EN_PATCH}" "insert-space-zh-en"

# update prebuilt
echo "updating prebuilt from ${PREBUILT_REPO}"
echo "prebuilt producer repo is ${PREBUILDER_REPO}"
# Do not overwrite locally rebuilt libraries or other uncommitted changes.
if [[ -n "$(git -C "${PREBUILT_DIR}" status --porcelain)" ]]; then
    echo "✗ prebuilt has local changes; refusing to move it to remote master" >&2
    exit 1
fi

git -C "${PREBUILT_DIR}" remote add boomker "${PREBUILT_REPO}" 2>/dev/null || \
    git -C "${PREBUILT_DIR}" remote set-url boomker "${PREBUILT_REPO}"
git -C "${PREBUILT_DIR}" fetch -v boomker master
git -C "${PREBUILT_DIR}" checkout --detach boomker/master
echo "✓ prebuilt updated to remote master: $(git -C "${PREBUILT_DIR}" rev-parse HEAD)"
