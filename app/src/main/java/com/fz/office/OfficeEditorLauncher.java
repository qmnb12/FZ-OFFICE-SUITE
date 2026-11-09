package com.fz.office;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import com.fz.office.fonts.FontManager;

/** Opens the native office engine only after the user explicitly taps Edit. */
public final class OfficeEditorLauncher {
    private static final String LO_ACTIVITY = "org.libreoffice.androidlib.LOActivity";
    private static final String READ_ONLY_EXTRA = "com.fz.office.READ_ONLY";

    private OfficeEditorLauncher() { }

    public static void openForEdit(Activity activity, Uri uri, String fileName) {
        if (activity == null || uri == null) return;
        try {
            FontManager.ensureBundledEngineFonts(activity);
        } catch (Exception fontError) {
            android.util.Log.w("FZOffice", "Hindi fonts could not be prepared before editing", fontError);
            Toast.makeText(activity, "Some Hindi fonts could not be prepared. Opening the editor anyway.",
                    Toast.LENGTH_LONG).show();
        }

        boolean hasWriteGrant = activity.checkUriPermission(uri, android.os.Process.myPid(),
                android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
        Intent editor = new Intent(Intent.ACTION_VIEW);
        editor.setClassName(activity.getPackageName(), LO_ACTIVITY);
        editor.setDataAndType(uri, resolveMimeType(activity, uri, fileName));
        int grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (hasWriteGrant) grantFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        editor.addFlags(grantFlags);
        editor.putExtra(READ_ONLY_EXTRA, false);
        try {
            activity.startActivity(editor);
            if (!hasWriteGrant) {
                Toast.makeText(activity,
                        "This provider did not grant direct write access. Edit the working copy and use Save As to keep it.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (android.content.ActivityNotFoundException missingEngine) {
            Toast.makeText(activity, "The native Collabora editor is not available in this build.",
                    Toast.LENGTH_LONG).show();
        } catch (SecurityException denied) {
            Toast.makeText(activity, "The document provider denied editing access. Reopen the file and try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String resolveMimeType(Activity activity, Uri uri, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docm")) return "application/vnd.ms-word.document.macroenabled.12";
        if (lower.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (lower.endsWith(".rtf")) return "application/rtf";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".ods")) return "application/vnd.oasis.opendocument.spreadsheet";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".odp")) return "application/vnd.oasis.opendocument.presentation";
        String supplied = activity.getContentResolver().getType(uri);
        return supplied == null || supplied.trim().isEmpty() ? "application/octet-stream" : supplied;
    }
}
