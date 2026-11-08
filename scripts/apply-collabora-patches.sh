#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COLLABORA="$ROOT/third_party/collabora"
PATCH_DIR="$ROOT/patches/collabora"

if ! git -C "$COLLABORA" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Collabora source submodule is not initialized." >&2
  echo "Run: git submodule update --init third_party/collabora" >&2
  exit 2
fi

# Keep these small patches pinned to the Collabora submodule revision.
PATCHES=(
  "$PATCH_DIR/0001-android-fontconfig-hindi-font-directory.patch"
  "$PATCH_DIR/0002-bundle-fz-hindi-fonts.patch"
)

for PATCH in "${PATCHES[@]}"; do
  if [[ ! -f "$PATCH" ]]; then
    echo "Required patch is missing: $PATCH" >&2
    exit 2
  fi
  if git -C "$COLLABORA" apply --reverse --check "$PATCH" >/dev/null 2>&1; then
    echo "Already applied: $(basename "$PATCH")"
    continue
  fi
  if ! git -C "$COLLABORA" apply --check "$PATCH"; then
    echo "Patch does not apply to the pinned Collabora source: $(basename "$PATCH")" >&2
    exit 2
  fi
  git -C "$COLLABORA" apply "$PATCH"
  echo "Applied: $(basename "$PATCH")"
done

python3 "$ROOT/scripts/patch-collabora-hindi.py" "$COLLABORA"
