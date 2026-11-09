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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fz.office.fonts.FontCenterActivity;
import com.fz.office.library.RecentDocumentsStore;
import com.fz.office.pdf.PdfViewerActivity;
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
    private static final String MIME_PDF = "application/pdf";

    private LinearLayout libraryList;
    private EditText librarySearch;
    private boolean showStarredOnly;
    private String libraryQuery = "";

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
        addTypeTile(rowTwo, "PDF", "PDF Reader", 0xffb94343);

        TextView supported = label("Word · Excel · PowerPoint · PDF · TXT", 11, MUTED, false);
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

        addLibrarySection(page);

        TextView footer = label("Read-only viewer · Original files stay unchanged · No account or ads", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(10), dp(27), dp(10), 0);
        page.addView(footer, match());
    }


    private void addLibrarySection(LinearLayout page) {
        TextView heading = label("Your Library", 20, TEXT, true);
        heading.setPadding(0, dp(30), 0, dp(5));
        page.addView(heading, match());
        TextView hint = label("Recent files stay on this device · Star important documents", 11, MUTED, false);
        hint.setPadding(0, 0, 0, dp(12));
        page.addView(hint, match());

        librarySearch = new EditText(this);
        librarySearch.setSingleLine(true);
        librarySearch.setTextSize(14);
        librarySearch.setTextColor(TEXT);
        librarySearch.setHintTextColor(MUTED);
        librarySearch.setHint("Search recent documents");
        librarySearch.setPadding(dp(15), dp(12), dp(15), dp(12));
        librarySearch.setBackground(round(SURFACE, 14));
        page.addView(librarySearch, match());
        librarySearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                libraryQuery = s == null ? "" : s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                renderLibrary();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabsParams = match();
        tabsParams.topMargin = dp(12);
        page.addView(tabs, tabsParams);
        TextView recent = libraryTabButton("Recent files", !showStarredOnly);
        TextView starred = libraryTabButton("Starred files", showStarredOnly);
        tabs.addView(recent, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams starredParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        starredParams.leftMargin = dp(9);
        tabs.addView(starred, starredParams);
        recent.setOnClickListener(v -> {
            showStarredOnly = false;
            updateLibraryTabs(tabs);
            renderLibrary();
        });
        starred.setOnClickListener(v -> {
            showStarredOnly = true;
            updateLibraryTabs(tabs);
            renderLibrary();
        });

        libraryList = new LinearLayout(this);
        libraryList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = match();
        listParams.topMargin = dp(10);
        page.addView(libraryList, listParams);
        renderLibrary();
    }

    private TextView libraryTabButton(String value, boolean active) {
        TextView button = label(value, 13, active ? BG : TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(42));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(round(active ? ACCENT : SURFACE, 14));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(value);
        return button;
    }

    private void updateLibraryTabs(LinearLayout tabs) {
        if (tabs.getChildCount() < 2) return;
        TextView recent = (TextView) tabs.getChildAt(0);
        TextView starred = (TextView) tabs.getChildAt(1);
        recent.setBackground(round(showStarredOnly ? SURFACE : ACCENT, 14));
        recent.setTextColor(showStarredOnly ? TEXT : BG);
        starred.setBackground(round(showStarredOnly ? ACCENT : SURFACE, 14));
        starred.setTextColor(showStarredOnly ? BG : TEXT);
    }

    private void renderLibrary() {
        if (libraryList == null) return;
        libraryList.removeAllViews();
        java.util.List<RecentDocumentsStore.Entry> entries = RecentDocumentsStore.getAll(this);
        int shown = 0;
        for (RecentDocumentsStore.Entry entry : entries) {
            if (showStarredOnly && !entry.starred) continue;
            String searchable = (entry.name + " " + entry.mimeType).toLowerCase(java.util.Locale.ROOT);
            if (!libraryQuery.isEmpty() && !searchable.contains(libraryQuery)) continue;
            addLibraryItem(entry);
            shown++;
        }
        if (shown == 0) {
            String message = libraryQuery.isEmpty()
                    ? (showStarredOnly ? "No starred documents yet. Tap ☆ on a file to keep it here."
                    : "Your opened documents will appear here.")
                    : "No documents match this search.";
            TextView empty = label(message, 12, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(25), dp(18), dp(25));
            empty.setBackground(round(SURFACE, 15));
            libraryList.addView(empty, match());
        }
        if (!showStarredOnly && !entries.isEmpty()) {
            TextView clear = label("Clear unstarred history", 11, ACCENT, true);
            clear.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            clear.setPadding(dp(8), dp(13), dp(4), dp(7));
            libraryList.addView(clear, match());
            clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear recent history?")
                    .setMessage("This removes unstarred items from FZ Office history only. Original files will not be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", (dialog, which) -> {
                        RecentDocumentsStore.clearUnstarred(this);
                        renderLibrary();
                    }).show());
        }
    }

    private void addLibraryItem(RecentDocumentsStore.Entry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(11), dp(9), dp(11));
        card.setBackground(round(SURFACE, 15));
        LinearLayout.LayoutParams cardParams = match();
        cardParams.bottomMargin = dp(8);
        libraryList.addView(card, cardParams);

        String extension = extensionOf(entry.name).toUpperCase(java.util.Locale.ROOT);
        if (extension.isEmpty()) extension = "FILE";
        TextView type = label(extension, 10, Color.WHITE, true);
        type.setGravity(Gravity.CENTER);
        type.setBackground(round(typeColor(extension), 10));
        card.addView(type, new LinearLayout.LayoutParams(dp(51), dp(45)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(11), 0, dp(5), 0);
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView name = label(entry.name, 13, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        details.addView(name, match());
        TextView opened = label("Opened " + relativeTime(entry.openedAt), 10, MUTED, false);
        opened.setPadding(0, dp(4), 0, 0);
        details.addView(opened, match());

        TextView star = label(entry.starred ? "★" : "☆", 21, entry.starred ? ACCENT : MUTED, true);
        star.setGravity(Gravity.CENTER);
        card.addView(star, new LinearLayout.LayoutParams(dp(34), dp(40)));
        star.setOnClickListener(v -> {
            RecentDocumentsStore.setStarred(this, entry.uri, !entry.starred);
            renderLibrary();
        });

        TextView share = label("↗", 19, TEXT, true);
        share.setGravity(Gravity.CENTER);
        card.addView(share, new LinearLayout.LayoutParams(dp(34), dp(40)));
        share.setOnClickListener(v -> shareDocument(entry));

        TextView remove = label("×", 21, MUTED, false);
        remove.setGravity(Gravity.CENTER);
        card.addView(remove, new LinearLayout.LayoutParams(dp(30), dp(40)));
        remove.setOnClickListener(v -> {
            RecentDocumentsStore.remove(this, entry.uri);
            renderLibrary();
        });

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> launchDocument(Uri.parse(entry.uri), entry.name, entry.mimeType));
    }

    private void shareDocument(RecentDocumentsStore.Entry entry) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(entry.mimeType == null || entry.mimeType.isEmpty() ? "*/*" : entry.mimeType);
            share.putExtra(Intent.EXTRA_STREAM, Uri.parse(entry.uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share document"));
        } catch (Exception exception) {
            Toast.makeText(this, "This file cannot be shared right now. Reopen it and try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private int typeColor(String extension) {
        if ("DOCX".equals(extension) || "DOC".equals(extension)) return 0xff287bd1;
        if ("XLSX".equals(extension) || "XLS".equals(extension) || "CSV".equals(extension) || "ODS".equals(extension)) return 0xff17865a;
        if ("PPTX".equals(extension) || "PPT".equals(extension) || "PPSX".equals(extension)) return 0xffd45c3d;
        if ("PDF".equals(extension)) return 0xffb94343;
        return 0xff687d8d;
    }

    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private String relativeTime(long timestamp) {
        long elapsed = Math.max(0, System.currentTimeMillis() - timestamp);
        long minutes = elapsed / 60000L;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hr ago";
        long days = hours / 24;
        if (days == 1) return "yesterday";
        if (days < 30) return days + " days ago";
        return new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp));
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
                MIME_DOCX,
                MIME_XLSX, "application/vnd.ms-excel",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "application/vnd.ms-excel.sheet.binary.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "application/vnd.oasis.opendocument.spreadsheet",
                "text/csv", "text/comma-separated-values",
                MIME_PPTX, "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.template",
                MIME_PDF, "text/plain"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_OPEN_DOCUMENT); }
        catch (Exception e) { Toast.makeText(this, "No compatible file picker was found.", Toast.LENGTH_LONG).show(); }
    }

    private boolean openIncomingDocument(Intent incoming) {
        if (incoming == null || !Intent.ACTION_VIEW.equals(incoming.getAction())) return false;
        Uri uri = incoming.getData();
        if (uri == null) return false;
        String name = queryDisplayName(uri);
        String mime = incoming.getType();
        int incomingFlags = incoming.getFlags();
        if ((incomingFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, incomingFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
        }
        if (!isSupportedDocument(name, mime)) {
            Toast.makeText(this, "This format is not supported by the FZ read-only viewer.",
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
            Toast.makeText(this, "Choose DOCX/TXT, supported spreadsheets, presentations or PDF.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) { }
        launchDocument(uri, name, mime);
    }

    private boolean isSupportedDocument(String name, String mime) {
        return isPdfDocument(name, mime) || isOfficeViewerDocument(name, mime)
                || isFzWriterViewerDocument(name, mime);
    }

    private boolean isPdfDocument(String name, String mime) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".pdf") || MIME_PDF.equalsIgnoreCase(mime);
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
                || type.equals("application/vnd.oasis.opendocument.spreadsheet")
                || type.equals("text/csv") || type.equals("text/comma-separated-values")
                || type.equals(MIME_PPTX)
                || type.equals("application/vnd.openxmlformats-officedocument.presentationml.slideshow")
                || type.equals("application/vnd.ms-powerpoint.presentation.macroenabled.12")
                || type.equals("application/vnd.openxmlformats-officedocument.presentationml.template");
    }

    private boolean isFzWriterViewerDocument(String name, String mime) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".docx") || lower.endsWith(".txt")
                || MIME_DOCX.equalsIgnoreCase(mime) || "text/plain".equalsIgnoreCase(mime);
    }

    private void launchDocument(Uri uri, String name, String mime) {
        try {
            Intent open;
            if (isPdfDocument(name, mime)) {
                open = new Intent(this, PdfViewerActivity.class);
                open.putExtra(PdfViewerActivity.EXTRA_DOCUMENT_URI, uri.toString());
                open.putExtra(PdfViewerActivity.EXTRA_DOCUMENT_NAME, name);
            } else if (isOfficeViewerDocument(name, mime)) {
                open = new Intent(this, OfficeViewerActivity.class);
                open.putExtra(OfficeViewerActivity.EXTRA_DOCUMENT_URI, uri.toString());
                open.putExtra(OfficeViewerActivity.EXTRA_DOCUMENT_NAME, name);
            } else if (isFzWriterViewerDocument(name, mime)) {
                open = new Intent(this, WriterActivity.class);
                open.putExtra(WriterActivity.EXTRA_DOCUMENT_URI, uri.toString());
            } else {
                Toast.makeText(this, "This format is not supported by the FZ read-only viewer.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(open);
            RecentDocumentsStore.record(this, uri, name, mime);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open this document. Check that the file is still available.",
                    Toast.LENGTH_LONG).show();
        }
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
