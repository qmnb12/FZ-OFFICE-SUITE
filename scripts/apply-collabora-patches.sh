#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COLLABORA="$ROOT/third_party/collabora"
PATCH="$ROOT/patches/collabora/0001-android-fontconfig-hindi-font-directory.patch"

if [[ ! -d "$COLLABORA/.git" ]]; then
  echo "Collabora source submodule is not initialized." >&2
  echo "Run: git submodule update --init third_party/collabora" >&2
  exit 2
fi

if git -C "$COLLABORA" apply --reverse --check "$PATCH" >/dev/null 2>&1; then
  echo "Hindi fontconfig patch is already applied."
  exit 0
fi

git -C "$COLLABORA" apply --check "$PATCH"
git -C "$COLLABORA" apply "$PATCH"
echo "Added app-private Hindi font directory to Collabora Android fontconfig."
