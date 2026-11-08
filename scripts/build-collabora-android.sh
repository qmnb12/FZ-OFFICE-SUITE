#!/usr/bin/env bash
# Native Collabora/LibreOffice Android build. Requires a Linux build host.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COLLABORA="$ROOT/third_party/collabora"
ANDROID_HOME="$(printenv ANDROID_HOME || printenv ANDROID_SDK_ROOT || true)"
NDK_VERSION="$(printenv NDK_VERSION || echo 29.0.14206865)"
ANDROID_ABI="$(printenv ANDROID_ABI || echo arm64-v8a)"
JOBS="$(printenv JOBS || echo 2)"
VERSION_CODE="$(printenv VERSION_CODE || echo 11)"
VERSION_NAME="$(printenv VERSION_NAME || echo 0.6.2.0)"
GRADLE_BIN="$(printenv GRADLE_BIN || echo gradle)"

if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME/ndk/$NDK_VERSION" ]]; then
  echo "Set ANDROID_HOME/ANDROID_SDK_ROOT and install NDK $NDK_VERSION." >&2
  exit 2
fi
if [[ ! -d "$COLLABORA/engine" || ! -f "$COLLABORA/android/README.md" ]]; then
  echo "Collabora source submodule is missing. Run git submodule update --init third_party/collabora." >&2
  exit 2
fi

PINNED_COMMIT="$(git -C "$ROOT" ls-tree HEAD third_party/collabora | awk '{print $3}')"
CHECKED_OUT_COMMIT="$(git -C "$COLLABORA" rev-parse HEAD)"
if [[ -n "$PINNED_COMMIT" && "$CHECKED_OUT_COMMIT" != "$PINNED_COMMIT" ]]; then
  echo "Collabora submodule revision mismatch." >&2
  echo "Expected: $PINNED_COMMIT" >&2
  echo "Checked out: $CHECKED_OUT_COMMIT" >&2
  exit 2
fi

# Generate the pinned/open-licence Devanagari assets before Collabora's Copy task.
"$GRADLE_BIN" --no-daemon :app:downloadHindiFonts
FONT_SOURCE="$ROOT/app/build/generated/assets/hindiFonts/writer/fonts"
if [[ ! -d "$FONT_SOURCE" ]]; then
  echo "Hindi font assets were not generated: $FONT_SOURCE" >&2
  exit 2
fi

# The selected Collabora Gradle patch reads this exact font directory.
export FZ_OFFICE_HINDI_FONT_DIR="$FONT_SOURCE"

bash "$ROOT/scripts/apply-collabora-patches.sh"

case "$ANDROID_ABI" in
  arm64-v8a) DISTRO="CPAndroidAarch64" ;;
  armeabi-v7a) DISTRO="CPAndroid" ;;
  x86_64) DISTRO="CPAndroidX86_64" ;;
  x86) DISTRO="CPAndroidX86" ;;
  *) echo "Unsupported Android ABI: $ANDROID_ABI" >&2; exit 2 ;;
esac

NDK_PATH="$ANDROID_HOME/ndk/$NDK_VERSION"
VERSION_LABEL="${VERSION_NAME}-${VERSION_CODE}"

cat > "$COLLABORA/engine/autogen.input" <<EOF
--with-android-ndk=$NDK_PATH
--with-android-sdk=$ANDROID_HOME
--with-distro=$DISTRO
--enable-sal-log
EOF

(
  cd "$COLLABORA/engine"
  ./autogen.sh
  make -j"$JOBS"
)

(
  cd "$COLLABORA"
  ./autogen.sh
  ./configure \
    --enable-androidapp \
    --with-app-name="FZ Office" \
    --with-app-package-name="com.fz.office" \
    --with-android-abi="$ANDROID_ABI" \
    --with-android-package-versioncode="$VERSION_LABEL" \
    --disable-branding
  make -j"$JOBS"
)

(
  cd "$COLLABORA/android"
  ./gradlew --no-daemon assembleDebug
)

APK="$(find "$COLLABORA/android" -type f -path '*/build/outputs/apk/*/*debug*.apk' -print -quit)"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Gradle finished but no debug APK was found." >&2
  exit 3
fi

mkdir -p "$ROOT/build/collabora"
cp "$APK" "$ROOT/build/collabora/FZ-Office-Collabora-${ANDROID_ABI}-debug.apk"
echo "APK: $ROOT/build/collabora/FZ-Office-Collabora-arm64-debug.apk"
