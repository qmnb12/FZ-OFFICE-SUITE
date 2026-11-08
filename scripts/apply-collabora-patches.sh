#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COLLABORA="$ROOT/third_party/collabora"
if [[ ! -d "$COLLABORA/.git" ]]; then
  echo "Collabora source submodule is not initialized." >&2
  echo "Run: git submodule update --init third_party/collabora" >&2
  exit 2
fi
for patch in "$ROOT"/patches/collabora/*.patch; do
  [[ -f "$patch" ]] || continue
  if git -C "$COLLABORA" apply --reverse --check "$patch" >/dev/null 2>&1; then
    echo "Already applied: $(basename "$patch")"
    continue
  fi
  git -C "$COLLABORA" apply --check "$patch"
  git -C "$COLLABORA" apply "$patch"
  echo "Applied: $(basename "$patch")"
done
