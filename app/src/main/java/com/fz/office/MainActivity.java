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

import com.fz.office.writer.WriterActivity;
import com.fz.office.fonts.FontCenterActivity;

public class MainActivity extends Activity {

    private static final int REQUEST_OPEN_DOCUMENT = 101;
    private static final int COLOR_BACKGROUND = Color.rgb(244, 247, 245);
    private static final int COLOR_TEXT = Color.rgb(25, 42, 37);
    private static final int COLOR_MUTED = Color.rgb(100, 117, 109);
    private static final int COLOR_PRIMARY = Color.rgb(23, 107, 91);
    private static final int COLOR_CARD = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
        buildDashboard();
    }

    private void buildDashboard() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(28), dp(22), dp(30));
        scrollView.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scrollView);

        TextView brand = text("FZ  /  OFFICE SUITE", 12, COLOR_PRIMARY, true);
        brand.setLetterSpacing(0.08f);
        page.addView(brand, matchWrap());

        TextView title = text("आपके documents, आपकी भाषा।", 27, COLOR_TEXT, true);
        title.setPadding(0, dp(12), 0, dp(5));
        page.addView(title, matchWrap());

        TextView subtitle = text(
                "A Hindi-first office suite, built for Android.",
                14, COLOR_MUTED, false
        );
        page.addView(subtitle, matchWrap());

        View gap = new View(this);
        page.addView(gap, new LinearLayout.LayoutParams(1, dp(22)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(19), dp(19), dp(19), dp(19));
        hero.setBackground(roundRect(Color.rgb(21, 65, 56), dp(20)));
        page.addView(hero, matchWrap());

        TextView heroTag = text("DOCUMENT VIEWER  ·  v0.5.0", 10,
                Color.rgb(190, 224, 211), true);
        hero.addView(heroTag, matchWrap());

        TextView heroTitle = text("Hindi documents, easy to read.", 22,
                Color.WHITE, true);
        heroTitle.setPadding(0, dp(13), 0, dp(7));
        hero.addView(heroTitle, matchWrap());

        TextView heroBody = text(
                "DOCX और TXT documents read-only खुलेंगे। Supported legacy Hindi text display के लिए अपने-आप Unicode में बदलेगा; original file untouched रहेगी।",
                13, Color.rgb(222, 237, 231), false
        );
        heroBody.setLineSpacing(dp(3), 1.0f);
        hero.addView(heroBody, matchWrap());

        addSectionTitle(page, "Your workspace", "WORKSPACE");
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(firstRow, matchWrap());

        addModuleCard(firstRow, "D", "Documents", "DOCX · TXT", "View only", 0);
        addModuleCard(firstRow, "▦", "Sheets", "Spreadsheets", "Engine pending", 1);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondRowParams = matchWrap();
        secondRowParams.topMargin = dp(10);
        page.addView(secondRow, secondRowParams);

        addModuleCard(secondRow, "P", "Slides", "Presentations", "Engine pending", 2);
        addModuleCard(secondRow, "PDF", "PDF tools", "View & export", "Not connected", 3);

        addSectionTitle(page, "Quick actions", "GET STARTED");
        LinearLayout openFile = actionCard(
                "Open a document",
                "Choose a DOCX or TXT file to view",
                "↗"
        );
        openFile.setOnClickListener(v -> openDocumentPicker());
        LinearLayout.LayoutParams openParams = matchWrap();
        openParams.topMargin = dp(10);
        page.addView(openFile, openParams);

        LinearLayout fontCenter = actionCard(
                "Hindi Font Center",
                "12 bundled Unicode fonts, custom font import and Hindi text tests",
                "अ"
        );
        LinearLayout.LayoutParams fontParams = matchWrap();
        fontParams.topMargin = dp(10);
        page.addView(fontCenter, fontParams);
        fontCenter.setOnClickListener(v -> startActivity(new Intent(this, FontCenterActivity.class)));

        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(15), dp(14), dp(15), dp(14));
        note.setBackground(roundRect(Color.rgb(231, 239, 235), dp(15)));
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(20);
        page.addView(note, noteParams);

        TextView noteTitle = text("What works in this build?", 14, COLOR_TEXT, true);
        note.addView(noteTitle, matchWrap());
        TextView noteBody = text(
                "Writer can detect likely Kruti Dev/DevLys font metadata and offer a Kruti Dev 010 Unicode conversion preview. "
                        + "Review converted text before saving. Other legacy encodings still need verified converters.",
                12, COLOR_MUTED, false
        );
        noteBody.setPadding(0, dp(5), 0, 0);
        noteBody.setLineSpacing(dp(2), 1.0f);
        note.addView(noteBody, matchWrap());

        TextView footer = text(
                "OFFLINE-FIRST  ·  NO ADS  ·  OPEN-SOURCE PROJECT",
                10, COLOR_MUTED, true
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(23), 0, 0);
        page.addView(footer, matchWrap());
    }

    private void addSectionTitle(LinearLayout parent, String title, String eyebrow) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(24);
        params.bottomMargin = dp(11);
        parent.addView(wrapper, params);

        TextView small = text(eyebrow, 9, COLOR_PRIMARY, true);
        small.setLetterSpacing(0.12f);
        wrapper.addView(small, matchWrap());

        TextView heading = text(title, 19, COLOR_TEXT, true);
        heading.setPadding(0, dp(3), 0, 0);
        wrapper.addView(heading, matchWrap());
    }

    private void addModuleCard(
            LinearLayout row,
            String symbol,
            String title,
            String detail,
            String status,
            int index
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(13));
        card.setBackground(roundRect(COLOR_CARD, dp(16)));
        card.setElevation(dp(1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        if (index % 2 == 0) {
            params.rightMargin = dp(5);
        } else {
            params.leftMargin = dp(5);
        }
        row.addView(card, params);

        TextView icon = text(symbol, 20, COLOR_PRIMARY, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(Color.rgb(231, 241, 236), dp(12)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView name = text(title, 16, COLOR_TEXT, true);
        name.setPadding(0, dp(12), 0, dp(2));
        card.addView(name, matchWrap());

        card.addView(text(detail, 11, COLOR_MUTED, false), matchWrap());

        TextView badge = text(status, 9, COLOR_PRIMARY, true);
        badge.setPadding(0, dp(10), 0, 0);
        card.addView(badge, matchWrap());

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (index == 0) {
                openDocumentPicker();
            } else {
                Toast.makeText(
                        this,
                        title + " is not connected yet.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private LinearLayout actionCard(String title, String subtitle, String symbol) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(roundRect(COLOR_CARD, dp(15)));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setFocusable(true);

        TextView icon = text(symbol, 20, COLOR_PRIMARY, true);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(12);
        card.addView(copy, copyParams);

        copy.addView(text(title, 14, COLOR_TEXT, true), matchWrap());
        TextView detail = text(subtitle, 11, COLOR_MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail, matchWrap());

        TextView arrow = text("›", 25, COLOR_MUTED, false);
        card.addView(arrow, wrapWrap());
        return card;
    }

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
        } catch (Exception exception) {
            Toast.makeText(this, "No compatible file picker was found.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_DOCUMENT && resultCode == RESULT_OK && data != null) {
            Uri selectedFile = data.getData();
            if (selectedFile == null) return;

            String name = queryDisplayName(selectedFile).toLowerCase();
            if (!name.endsWith(".docx") && !name.endsWith(".txt")) {
                Toast.makeText(this, "This Writer build supports DOCX and TXT files only.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(selectedFile, flags);
            } catch (Exception ignored) { }

            Intent open = new Intent(this, WriterActivity.class);
            open.putExtra(WriterActivity.EXTRA_DOCUMENT_URI, selectedFile.toString());
            startActivity(open);
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null) return name;
                }
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
