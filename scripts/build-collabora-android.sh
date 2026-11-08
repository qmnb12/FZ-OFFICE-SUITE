#!/usr/bin/env bash
# Native Collabora/LibreOffice Android build.
# Requires a Linux build host with adequate SSD/RAM and upstream build dependencies.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COLLABORA="$ROOT/third_party/collabora"
ANDROID_HOME="$(printenv ANDROID_HOME || printenv ANDROID_SDK_ROOT || true)"
NDK_VERSION="$(printenv NDK_VERSION || echo 29.0.14206865)"
ANDROID_ABI="$(printenv ANDROID_ABI || echo arm64-v8a)"
JOBS="$(printenv JOBS || echo 2)"
VERSION_NAME="$(printenv VERSION_NAME || echo 0.6.0)"
VERSION_CODE="$(printenv VERSION_CODE || echo 9)"
GRADLE_CMD="$(printenv GRADLE_CMD || echo gradle)"
MIN_FREE_GB="$(printenv MIN_FREE_GB || echo 100)"

if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME/ndk/$NDK_VERSION" ]]; then
  echo "Set ANDROID_HOME/ANDROID_SDK_ROOT and install NDK $NDK_VERSION." >&2
  exit 2
fi
if [[ ! -d "$ANDROID_HOME/platforms/android-37" || ! -d "$ANDROID_HOME/platforms/android-36" ]]; then
  echo "Install Android SDK platforms 36 and 37." >&2
  exit 2
fi
if [[ ! -d "$COLLABORA/engine" || ! -f "$COLLABORA/android/README.md" ]]; then
  echo "Collabora source submodule is missing. Run git submodule update --init third_party/collabora." >&2
  exit 2
fi

AVAILABLE_GB="$(df -BG --output=avail "$ROOT" | tail -n 1 | tr -cd '0-9')"
if [[ -z "$AVAILABLE_GB" || "$AVAILABLE_GB" -lt "$MIN_FREE_GB" ]]; then
  echo "Native LibreOffice build needs at least $MIN_FREE_GB GB free disk; found $AVAILABLE_GB GB." >&2
  echo "Use a configured Linux larger runner/build host. Standard GitHub-hosted runners expose only 14 GB SSD." >&2
  exit 10
fi

# Download and verify the OFL Devanagari pack using FZ's pinned Google Fonts task.
(
  cd "$ROOT"
  "$GRADLE_CMD" --no-daemon :app:downloadHindiFonts
)
FZ_OFFICE_HINDI_FONT_DIR="$ROOT/app/build/generated/assets/hindiFonts/writer/fonts"
if [[ ! -d "$FZ_OFFICE_HINDI_FONT_DIR" ]]; then
  echo "Verified Hindi font pack was not generated." >&2
  exit 3
fi
export FZ_OFFICE_HINDI_FONT_DIR

"$ROOT/scripts/apply-collabora-patches.sh"

case "$ANDROID_ABI" in
  arm64-v8a) DISTRO="CPAndroidAarch64" ;;
  armeabi-v7a) DISTRO="CPAndroid" ;;
  x86_64) DISTRO="CPAndroidX86_64" ;;
  x86) DISTRO="CPAndroidX86" ;;
  *) echo "Unsupported Android ABI: $ANDROID_ABI" >&2; exit 2 ;;
esac

NDK_PATH="$ANDROID_HOME/ndk/$NDK_VERSION"
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
    --with-android-package-versioncode="$VERSION_NAME-$VERSION_CODE" \
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
  exit 4
fi

mkdir -p "$ROOT/build/collabora"
cp "$APK" "$ROOT/build/collabora/FZ-Office-Collabora-$ANDROID_ABI-debug.apk"
echo "APK: $ROOT/build/collabora/FZ-Office-Collabora-$ANDROID_ABI-debug.apk"
