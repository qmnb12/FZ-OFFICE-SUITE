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

shopt -s nullglob
PATCHES=("$PATCH_DIR"/*.patch)
if [[ ${#PATCHES[@]} -eq 0 ]]; then
  echo "No Collabora integration patches were found." >&2
  exit 2
fi

for PATCH in "${PATCHES[@]}"; do
  if git -C "$COLLABORA" apply --reverse --check "$PATCH" >/dev/null 2>&1; then
    echo "Already applied: $(basename "$PATCH")"
    continue
  fi
  git -C "$COLLABORA" apply --check "$PATCH"
  git -C "$COLLABORA" apply "$PATCH"
  echo "Applied: $(basename "$PATCH")"
done
