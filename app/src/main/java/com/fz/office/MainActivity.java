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
    private static final int BG = Color.rgb(15, 18, 20);
    private static final int SURFACE = Color.rgb(29, 34, 37);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(169, 179, 183);
    private static final int ACCENT = Color.rgb(31, 190, 174);

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
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(34), dp(24), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -1));
        setContentView(scroll);

        TextView brand = label("FZ WORK SUITE", 18, TEXT, true);
        brand.setLetterSpacing(0.08f);
        page.addView(brand, match());
        TextView subtitle = label("Documents · Hindi-first viewer", 12, MUTED, false);
        subtitle.setPadding(0, dp(6), 0, dp(34));
        page.addView(subtitle, match());

        TextView headline = label("Take a look at any document.", 23, TEXT, true);
        headline.setGravity(Gravity.CENTER);
        headline.setPadding(0, 0, 0, dp(30));
        page.addView(headline, match());

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(grid, match());
        addTypeTile(grid, "DOC", "Word", 0xff2588df, true);
        addTypeTile(grid, "TXT", "Text", 0xff647d8d, true);

        TextView status = label("Supported now: DOCX and TXT", 12, MUTED, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(24), 0, dp(18));
        page.addView(status, match());

        TextView open = actionButton("Open a file", true);
        page.addView(open, match());
        open.setOnClickListener(v -> openDocumentPicker());

        TextView fonts = actionButton("अ   Hindi Font Center", false);
        LinearLayout.LayoutParams fp = match();
        fp.topMargin = dp(12);
        page.addView(fonts, fp);
        fonts.setOnClickListener(v -> startActivity(new Intent(this, FontCenterActivity.class)));

        TextView tip = label("Choose a DOCX or TXT file to view. No ads. Works offline.", 11, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(12), dp(28), dp(12), 0);
        page.addView(tip, match());
    }

    private void addTypeTile(LinearLayout row, String type, String name, int color, boolean enabled) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(12), dp(18), dp(12), dp(18));
        tile.setBackground(round(SURFACE, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(116), 1f);
        if (row.getChildCount() > 0) p.leftMargin = dp(10);
        row.addView(tile, p);
        TextView badge = label(type, 18, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(color, 13));
        tile.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView title = label(name, 13, TEXT, true);
        title.setPadding(0, dp(9), 0, 0);
        tile.addView(title);
        tile.setOnClickListener(v -> openDocumentPicker());
    }

    private TextView actionButton(String value, boolean primary) {
        TextView button = label(value, 15, primary ? Color.rgb(8, 28, 27) : TEXT, true);
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
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"
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
        boolean docx = name.toLowerCase(java.util.Locale.ROOT).endsWith(".docx")
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime);
        boolean txt = name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt") || "text/plain".equals(mime);
        if (!docx && !txt) {
            Toast.makeText(this, "This build supports DOCX and TXT files only.", Toast.LENGTH_LONG).show();
            return true;
        }
        Intent open = new Intent(this, WriterActivity.class);
        open.putExtra(WriterActivity.EXTRA_DOCUMENT_URI, uri.toString());
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(open); }
        catch (Exception e) { Toast.makeText(this, "Could not open this document.", Toast.LENGTH_LONG).show(); }
        return true;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_DOCUMENT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = queryDisplayName(uri).toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".docx") && !name.endsWith(".txt")) {
            Toast.makeText(this, "Only DOCX and TXT are supported right now.", Toast.LENGTH_LONG).show();
            return;
        }
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) { }
        Intent open = new Intent(this, WriterActivity.class);
        open.putExtra(WriterActivity.EXTRA_DOCUMENT_URI, uri.toString());
        startActivity(open);
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) return c.getString(i);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
