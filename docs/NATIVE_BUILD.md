# Building the native FZ Office Android app

## The two Android builds are different

- android-build.yml runs tests and builds a **shell-preview** APK. It does not contain LibreOfficeKit and is not the final Office app.
- collabora-native-build.yml is the integrated native build. It checks out the pinned Collabora submodule, copies FZ's Java source/assets into the Collabora Android app, sets FZ MainActivity as the sole launcher, routes Office documents to the internal read-only LOActivity, builds the LibreOffice C/C++ engine and packages one FZ Office APK.

Do not identify the shell-preview APK as a native-engine build.

## Run from GitHub Actions

1. Open the FZ-OFFICE-SUITE repository on GitHub.
2. Open Actions.
3. Select **Collabora native Android build**.
4. Select **Run workflow** on the main branch.
5. When the run succeeds, download the **FZ-Office-Collabora-arm64** artifact.

The workflow targets arm64-v8a only, matching the primary ABI of the user's Android tablet. The native build can take significantly longer and use more disk/RAM than the lightweight app build.

## COTG support

Code On The Go can edit/pull the FZ Office repository and install the resulting APK. It cannot build LibreOffice's native C/C++ engine. No separate IDE is required on the tablet, but the native engine must be compiled by a Linux cloud runner.

## Build command on a prepared Linux host

After installing the SDK, NDK 29.0.14206865, CMake 3.22.1, JDK 21, Gradle 9.6.1 and the Linux build dependencies:

    git submodule update --init third_party/collabora
    ANDROID_HOME="\${HOME}/Android/Sdk" \
    NDK_VERSION=29.0.14206865 \
    ANDROID_ABI=arm64-v8a \
    JOBS=2 \
    VERSION_NAME=0.6.6.0 \
    VERSION_CODE=15 \
    GRADLE_BIN=gradle \
    bash scripts/build-collabora-android.sh

The script:
- verifies the checked-out submodule matches the gitlink pin;
- applies 0001 Fontconfig user-font directory, 0002 bundled Devanagari fonts and stable package-name patches, and 0003 native Hindi ZIP import menu;

- generates the Devanagari assets from pinned Google Fonts binaries and verifies hashes;
- applies the FZ Fontconfig, bundled-font and native Font Center patches;
- merges the FZ shell, local library, font center and fallback reader classes/assets into the native app;
- validates that FZ MainActivity is the only launcher and LOActivity is internal;
- builds the Android LibreOffice engine;
- configures package name com.fz.office;
- packages the Collabora Android app and exports the APK.

## Resource limits

The standard GitHub-hosted runner can be tight for a large native office build. The workflow frees unused preinstalled software first, uses two build jobs and has a six-hour limit. If the job fails for memory/disk limits, a larger Linux runner or dedicated native build host is required. Do not fall back to the old HTML parser and call it equivalent.
