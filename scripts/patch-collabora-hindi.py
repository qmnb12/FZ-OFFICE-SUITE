#!/usr/bin/env python3
"""Apply small, idempotent FZ Office integrations to the pinned Collabora Android source."""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one source anchor, found {count}")
    return text.replace(old, new, 1)


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def patch_menu(root: Path) -> None:
    path = root / "android/app/src/main/res/menu/view_menu.xml"
    text = path.read_text(encoding="utf-8")
    marker = "</menu>"
    item = """    <item android:id="@+id/action_hindi_fonts"
        android:title="Hindi Font Center"
        app:showAsAction="never"/>

"""
    if "action_hindi_fonts" not in text:
        if text.count(marker) != 1:
            raise SystemExit("view_menu.xml: expected one </menu> anchor")
        text = text.replace(marker, item + marker, 1)
        write(path, text)


def patch_activity(root: Path) -> None:
    path = root / "android/app/src/main/java/org/libreoffice/androidapp/ui/LibreOfficeUIActivity.java"
    text = path.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "    private static final int OPEN_FILE_REQUEST_CODE = 43;\n",
        "    private static final int OPEN_FILE_REQUEST_CODE = 43;\n"
        "    private static final int IMPORT_HINDI_FONTS_REQUEST_CODE = 47;\n"
        "    private static final long MAX_HINDI_FONT_ARCHIVE_BYTES = 180L * 1024L * 1024L;\n"
        "    private static final int MAX_HINDI_FONT_FILE_BYTES = 12 * 1024 * 1024;\n"
        "    private static final int MAX_HINDI_FONTS = 1200;\n",
        "Hindi font import constants",
    )

    text = replace_once(
        text,
        """        if (item.getItemId() == R.id.action_open_file) {
            openDocument();
            return true;
        }

        return super.onOptionsItemSelected(item);""",
        """        if (item.getItemId() == R.id.action_open_file) {
            openDocument();
            return true;
        }
        if (item.getItemId() == R.id.action_hindi_fonts) {
            showHindiFontLibrary();
            return true;
        }

        return super.onOptionsItemSelected(item);""",
        "Hindi font menu action",
    )

    text = replace_once(
        text,
        """    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {""",
        """    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_HINDI_FONTS_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importHindiFontArchive(data.getData());
            }
            return;
        }
        switch (requestCode) {""",
        "Hindi font import result",
    )

    # Keep the source file read-only even when the document provider could grant writes.
    text = replace_once(
        text,
        """    /** Build Intent to edit a Uri. */
    public Intent getIntentToEdit(Uri uri) {
        Intent i = new Intent(Intent.ACTION_EDIT, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);""",
        """    /** Build the default read-only document-view Intent. */
    public Intent getIntentToEdit(Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.putExtra("com.fz.office.READ_ONLY", true);""",
        "read-only open intent",
    )

    helper = r'''
    private void showHindiFontLibrary() {
        File directory = new File(getApplicationInfo().dataDir, "user/fonts");
        File[] files = directory.listFiles((dir, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".ttf") || lower.endsWith(".otf");
        });
        if (files == null) files = new File[0];
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        StringBuilder message = new StringBuilder(
                "Import licensed TTF/OTF fonts for Unicode Hindi and legacy documents. "
                + "Fonts are stored privately on this device. The original document text is never converted.\n\n");
        message.append("Installed fonts: ").append(files.length);
        int shown = Math.min(files.length, 24);
        for (int i = 0; i < shown; i++) {
            message.append("\n• ").append(files[i].getName());
        }
        if (files.length > shown) message.append("\n… and ").append(files.length - shown).append(" more");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hindi Font Center")
                .setMessage(message.toString())
                .setPositiveButton("Import ZIP font pack", (dialog, which) -> chooseHindiFontArchive())
                .setNegativeButton("Close", null)
                .show();
    }

    private void chooseHindiFontArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, IMPORT_HINDI_FONTS_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show();
        }
    }

    private void importHindiFontArchive(Uri uri) {
        Toast.makeText(this, "Importing Hindi font pack…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int imported = 0;
            int duplicates = 0;
            int invalid = 0;
            int ignored = 0;
            long expanded = 0;
            String error = null;
            File directory = new File(getApplicationInfo().dataDir, "user/fonts");

            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new java.io.IOException("Cannot create the private user/fonts folder.");
                }
                java.util.Set<String> knownHashes = new java.util.HashSet<>();
                File[] current = directory.listFiles();
                if (current != null) {
                    for (File file : current) {
                        String lower = file.getName().toLowerCase(java.util.Locale.ROOT);
                        if (file.isFile() && (lower.endsWith(".ttf") || lower.endsWith(".otf"))) {
                            knownHashes.add(sha256FontFile(file));
                        }
                    }
                }

                InputStream source = getContentResolver().openInputStream(uri);
                if (source == null) throw new java.io.IOException("The selected ZIP cannot be read.");
                try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(source)) {
                    java.util.zip.ZipEntry entry;
                    int entries = 0;
                    byte[] buffer = new byte[8192];
                    while ((entry = zip.getNextEntry()) != null) {
                        entries++;
                        if (entries > 20000) throw new java.io.IOException("ZIP contains too many entries.");
                        if (entry.isDirectory()) {
                            zip.closeEntry();
                            continue;
                        }

                        String entryName = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                        int slash = entryName.lastIndexOf('/');
                        String baseName = slash >= 0 ? entryName.substring(slash + 1) : entryName;
                        String lower = baseName.toLowerCase(java.util.Locale.ROOT);
                        boolean isFont = lower.endsWith(".ttf") || lower.endsWith(".otf");
                        java.io.ByteArrayOutputStream bytes = isFont ? new java.io.ByteArrayOutputStream() : null;
                        long entryBytes = 0;
                        boolean tooLarge = false;
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            expanded += read;
                            entryBytes += read;
                            if (expanded > MAX_HINDI_FONT_ARCHIVE_BYTES) {
                                throw new java.io.IOException("Expanded ZIP exceeds the 180 MB safety limit.");
                            }
                            if (isFont && !tooLarge) {
                                if (entryBytes <= MAX_HINDI_FONT_FILE_BYTES) {
                                    bytes.write(buffer, 0, read);
                                } else {
                                    tooLarge = true;
                                    bytes.reset();
                                }
                            }
                        }
                        zip.closeEntry();

                        if (!isFont) {
                            ignored++;
                            continue;
                        }
                        if (tooLarge || imported + duplicates + invalid >= MAX_HINDI_FONTS) {
                            invalid++;
                            continue;
                        }

                        byte[] fontData = bytes.toByteArray();
                        if (!hasHindiFontSignature(fontData)) {
                            invalid++;
                            continue;
                        }
                        String hash = sha256FontBytes(fontData);
                        if (knownHashes.contains(hash)) {
                            duplicates++;
                            continue;
                        }

                        String safeName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
                        if (safeName.length() > 90) safeName = safeName.substring(safeName.length() - 90);
                        File destination = uniqueHindiFontFile(directory, safeName);
                        try (FileOutputStream output = new FileOutputStream(destination)) {
                            output.write(fontData);
                            output.flush();
                        }
                        try {
                            android.graphics.Typeface.createFromFile(destination);
                            knownHashes.add(hash);
                            imported++;
                        } catch (Exception invalidFont) {
                            destination.delete();
                            invalid++;
                        }
                    }
                }
                clearHindiFontconfigCache();
            } catch (Exception exception) {
                error = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            }

            final int importedCount = imported;
            final int duplicateCount = duplicates;
            final int invalidCount = invalid;
            final int ignoredCount = ignored;
            final String errorMessage = error;
            runOnUiThread(() -> {
                String result = "Imported: " + importedCount
                        + "\nAlready present: " + duplicateCount
                        + "\nInvalid/too large: " + invalidCount
                        + "\nOther files ignored: " + ignoredCount
                        + "\n\nFonts stay on this device. Close and reopen documents so Fontconfig can rescan.";
                if (errorMessage != null) result += "\n\nImport stopped: " + errorMessage;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Hindi font pack")
                        .setMessage(result)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }, "HindiFontImport").start();
    }

    private static boolean hasHindiFontSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        String tag = new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return (bytes[0] == 0 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0)
                || "OTTO".equals(tag) || "true".equals(tag) || "typ1".equals(tag);
    }

    private static File uniqueHindiFontFile(File directory, String name) {
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : ".ttf";
        File candidate = new File(directory, stem + ext);
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(directory, stem + "-" + suffix + ext);
            suffix++;
        }
        return candidate;
    }

    private static String sha256FontBytes(byte[] bytes) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte item : hash) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }

    private static String sha256FontFile(File file) throws Exception {
        try (InputStream input = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return sha256FontBytes(output.toByteArray());
        }
    }

    private void clearHindiFontconfigCache() {
        File cache = new File(getApplicationInfo().dataDir, "fontconfig");
        deleteHindiFontCacheTree(cache);
    }

    private void deleteHindiFontCacheTree(File directory) {
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteHindiFontCacheTree(child);
                else child.delete();
            }
        }
    }
'''

    marker = "\n}\n\n/* vim:set shiftwidth=4 softtabstop=4 expandtab: */"
    if "private void showHindiFontLibrary()" not in text:
        if text.count(marker) != 1:
            raise SystemExit("LibreOfficeUIActivity.java: class-end insertion anchor missing")
        text = text.replace(marker, "\n" + helper + marker, 1)

    write(path, text)


def patch_lo_activity(root: Path) -> None:
    path = root / "android/lib/src/main/java/org/libreoffice/androidlib/LOActivity.java"
    text = path.read_text(encoding="utf-8")
    text = replace_once(
        text,
        """            if (getIntent().getData().getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                isDocEditable = true;""",
        """            if (getIntent().getData().getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                isDocEditable = !getIntent().getBooleanExtra("com.fz.office.READ_ONLY", false);""",
        "content URI read-only mode",
    )
    text = replace_once(
        text,
        """                if ((getIntent().getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {""",
        """                if (isDocEditable && (getIntent().getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {""",
        "read-only writable-copy guard",
    )
    text = replace_once(
        text,
        """            } else if (getIntent().getData().getScheme().equals(ContentResolver.SCHEME_FILE)) {
                isDocEditable = true;""",
        """            } else if (getIntent().getData().getScheme().equals(ContentResolver.SCHEME_FILE)) {
                isDocEditable = !getIntent().getBooleanExtra("com.fz.office.READ_ONLY", false);""",
        "file URI read-only mode",
    )
    write(path, text)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch-collabora-hindi.py <Collabora source root>")
    root = Path(sys.argv[1]).resolve()
    patch_menu(root)
    patch_activity(root)
    patch_lo_activity(root)
    print("FZ Office Hindi font import and read-only integration applied.")


if __name__ == "__main__":
    main()
