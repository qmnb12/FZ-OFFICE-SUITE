package com.fz.office;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fz.office.fonts.FontCenterActivity;
import com.fz.office.viewer.OfficeViewerActivity;
import com.fz.office.writer.WriterActivity;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_DOCUMENT = 101;
    private static final int BG = Color.rgb(15, 20, 22);
    private static final int SURFACE = Color.rgb(29, 37, 39);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(169, 181, 181);
    private static final int ACCENT = Color.rgb(49, 190, 174);
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (openIncomingDocument(getIntent())) { finish(); return; }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildHome();
    }

    private void buildHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(30), dp(22), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -1));
        setContentView(scroll);
        applySafeAreaInsets(scroll);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(brandRow, match());
        TextView mark = label("FZ", 17, BG, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(ACCENT, 14));
        brandRow.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(12), 0, 0, 0);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView brand = label("FZ WORK SUITE", 17, TEXT, true);
        brand.setLetterSpacing(0.07f);
        brandText.addView(brand, match());
        TextView subtitle = label("Hindi-first · Offline · Private", 11, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        brandText.addView(subtitle, match());

        TextView headline = label("All your files. One reader.", 25, TEXT, true);
        headline.setPadding(0, dp(34), 0, dp(7));
        page.addView(headline, match());
        TextView description = label("Documents को साफ़ पढ़ें — Hindi और English दोनों में.", 13, MUTED, false);
        description.setPadding(0, 0, 0, dp(25));
        page.addView(description, match());

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(rowOne, match());
        addTypeTile(rowOne, "DOCX", "Word", 0xff287bd1);
        addTypeTile(rowOne, "XLSX", "Excel", 0xff17865a);

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowTwoParams = match();
        rowTwoParams.topMargin = dp(11);
        page.addView(rowTwo, rowTwoParams);
        addTypeTile(rowTwo, "PPTX", "Slides", 0xffd45c3d);
        addTypeTile(rowTwo, "TXT", "Text", 0xff687d8d);

        TextView supported = label("DOCX · XLSX / XLS · CSV · ODS · PPTX · TXT", 11, MUTED, false);
        supported.setGravity(Gravity.CENTER);
        supported.setPadding(0, dp(22), 0, dp(18));
        page.addView(supported, match());

        TextView open = actionButton("Open a document", true);
        page.addView(open, match());
        open.setOnClickListener(v -> openDocumentPicker());

        TextView fonts = actionButton("अ   Hindi Font Center", false);
        LinearLayout.LayoutParams fontsParams = match();
        fontsParams.topMargin = dp(11);
        page.addView(fonts, fontsParams);
        fonts.setOnClickListener(v -> startActivity(new Intent(this, FontCenterActivity.class)));

        TextView footer = label("Read-only viewer · Original files stay unchanged · No account or ads", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(10), dp(27), dp(10), 0);
        page.addView(footer, match());
    }

    private void applySafeAreaInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    private void addTypeTile(LinearLayout row, String type, String name, int color) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(12), dp(17), dp(12), dp(17));
        tile.setBackground(round(SURFACE, 19));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(122), 1f);
        if (row.getChildCount() > 0) params.leftMargin = dp(11);
        row.addView(tile, params);
        TextView badge = label(type, 17, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(color, 13));
        tile.addView(badge, new LinearLayout.LayoutParams(dp(70), dp(53)));
        TextView title = label(name, 13, TEXT, true);
        title.setPadding(0, dp(10), 0, 0);
        tile.addView(title);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(v -> openDocumentPicker());
    }

    private TextView actionButton(String value, boolean primary) {
        TextView button = label(value, 14, primary ? BG : TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(16), dp(16), dp(16));
        button.setBackground(round(primary ? ACCENT : SURFACE, 15));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                MIME_DOCX, MIME_XLSX, "application/vnd.ms-excel",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "application/vnd.ms-excel.sheet.binary.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "application/vnd.oasis.opendocument.spreadsheet", "text/csv", MIME_PPTX,
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.template",
                "text/plain"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_OPEN_DOCUMENT); }
        catch (Exception e) { Toast.makeText(this, "No compatible file picker was found.", Toast.LENGTH_LONG).show(); }
    }

    private boolean openIncomingDocument(Intent incoming) {
        if (incoming == null || !Intent.ACTION_VIEW.equals(incoming.getAction())) return false;
        Uri uri = incoming.getData();
        if (uri == null) return false;
        String name = queryDisplayName(uri);
        String mime = incoming.getType();
        if (!isSupportedDocument(name, mime)) {
            Toast.makeText(this, "Supported: DOCX, XLS/XLSX, CSV, ODS, PPTX and TXT.",
                    Toast.LENGTH_LONG).show();
            return true;
        }
        launchDocument(uri, name, mime);
        return true;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_DOCUMENT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = queryDisplayName(uri);
        String mime = getContentResolver().getType(uri);
        if (!isSupportedDocument(name, mime)) {
            Toast.makeText(this, "Choose DOCX, XLS/XLSX, CSV, ODS, PPTX or TXT.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) { }
        launchDocument(uri, name, mime);
    }

    private boolean isSupportedDocument(String name, String mime) {
        return isOfficeViewerDocument(name, mime) || isWriterDocument(name, mime);
    }

    private boolean isOfficeViewerDocument(String name, String mime) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".xlsm")
                || lower.endsWith(".xlsb") || lower.endsWith(".xltx") || lower.endsWith(".csv")
                || lower.endsWith(".ods") || lower.endsWith(".pptx") || lower.endsWith(".ppsx")
                || lower.endsWith(".pptm") || lower.endsWith(".potx")) return true;
        if (mime == null) return false;
        String type = mime.toLowerCase(java.util.Locale.ROOT);
        return type.equals(MIME_XLSX) || type.equals("application/vnd.ms-excel")
                || type.equals("application/vnd.ms-excel.sheet.macroenabled.12")
                || type.equals("application/vnd.ms-excel.sheet.binary.macroenabled.12")
                || type.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.template")
                || type.equals("application/vnd.oasis.opendocument.spreadsheet") || type.equals("text/csv")
                || type.equals(MIME_PPTX)
                || type.equals("application/vnd.openxmlformats-officedocument.presentationml.slideshow")
                || type.equals("application/vnd.ms-powerpoint.presentation.macroenabled.12")
                || type.equals("application/vnd.openxmlformats-officedocument.presentationml.template");
    }

    private boolean isWriterDocument(String name, String mime) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".docx") || lower.endsWith(".txt")
                || MIME_DOCX.equals(mime) || "text/plain".equals(mime);
    }

    private void launchDocument(Uri uri, String name, String mime) {
        Intent open;
        if (isOfficeViewerDocument(name, mime)) {
            open = new Intent(this, OfficeViewerActivity.class);
            open.putExtra(OfficeViewerActivity.EXTRA_DOCUMENT_URI, uri.toString());
            open.putExtra(OfficeViewerActivity.EXTRA_DOCUMENT_NAME, name);
        } else {
            open = new Intent(this, WriterActivity.class);
            open.putExtra(WriterActivity.EXTRA_DOCUMENT_URI, uri.toString());
        }
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(open); }
        catch (Exception e) { Toast.makeText(this, "Could not open this document.", Toast.LENGTH_LONG).show(); }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "Document" : uri.getLastPathSegment();
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
