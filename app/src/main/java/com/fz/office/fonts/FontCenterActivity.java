package com.fz.office.fonts;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FontCenterActivity extends Activity {
    private static final int REQUEST_IMPORT_FONT = 410;
    private final ExecutorService importer = Executors.newSingleThreadExecutor();
    private LinearLayout fontList;
    private TextView previewInput;
    private TextView selectedStatus;

    private static final int INK = Color.rgb(26, 50, 42);
    private static final int MUTED = Color.rgb(101, 119, 109);
    private static final int PRIMARY = Color.rgb(23, 107, 91);
    private static final int BACKGROUND = Color.rgb(244, 247, 245);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        renderFonts();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView brand = text("FZ OFFICE  /  TYPOGRAPHY", 10, PRIMARY, true);
        brand.setLetterSpacing(0.12f);
        root.addView(brand);

        TextView title = text("Hindi Font Center", 25, INK, true);
        title.setPadding(0, dp(7), 0, dp(3));
        root.addView(title);

        TextView intro = text(
                "देवनागरी text के लिए fonts चुनें, preview देखें और अपना TTF/OTF font import करें। "
                        + "Font files इस device के private storage में रखी जाती हैं।",
                13, MUTED, false
        );
        intro.setLineSpacing(dp(3), 1f);
        root.addView(intro);

        Button importButton = button("+  Import TTF / OTF", PRIMARY, Color.WHITE);
        LinearLayout.LayoutParams importParams = wrapMatch();
        importParams.topMargin = dp(15);
        root.addView(importButton, importParams);
        importButton.setOnClickListener(v -> chooseFontFile());

        selectedStatus = text("", 12, PRIMARY, true);
        selectedStatus.setPadding(0, dp(10), 0, dp(4));
        root.addView(selectedStatus);

        addHeading(root, "Preview text", "SHAPING CHECK");
        previewInput = text(FontManager.HINDI_SAMPLE + "\n"
                + FontManager.SHAPING_SAMPLE + "\n"
                + FontManager.JOINER_SAMPLE + "\n"
                + FontManager.NUMERAL_SAMPLE, 14, INK, false);
        previewInput.setPadding(dp(13), dp(12), dp(13), dp(12));
        previewInput.setBackground(roundRect(Color.WHITE, dp(13)));
        previewInput.setLineSpacing(dp(3), 1f);
        root.addView(previewInput, wrapMatch());

        addHeading(root, "Bundled Unicode fonts", "12 FAMILIES");
        TextView bundledInfo = text(
                "These open-license fonts are bundled by the project and work offline after installation. "
                        + "Some display faces have narrower glyph coverage; Noto Sans / Noto Serif are the broad-coverage defaults.",
                12, MUTED, false
        );
        bundledInfo.setPadding(0, 0, 0, dp(10));
        root.addView(bundledInfo);

        fontList = new LinearLayout(this);
        fontList.setOrientation(LinearLayout.VERTICAL);
        root.addView(fontList, wrapMatch());

        addHeading(root, "Legacy Hindi fonts", "IMPORT AND CONVERT");
        LinearLayout legacyCard = new LinearLayout(this);
        legacyCard.setOrientation(LinearLayout.VERTICAL);
        legacyCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        legacyCard.setBackground(roundRect(Color.WHITE, dp(13)));
        root.addView(legacyCard, wrapMatch());

        legacyCard.addView(text(
                "Kruti Dev · DevLys · Chanakya · Shusha · Shree Lipi · Akruti · APS-DV",
                14, INK, true
        ));
        TextView legacyInfo = text(
                "इनके font files proprietary या अलग-अलग license वाले हो सकते हैं, इसलिए project इन्हें bundle नहीं करता। "
                        + "अपनी legally obtained TTF/OTF file import कर सकते हैं। Writer में अभी Kruti Dev 010 के लिए "
                        + "Unicode conversion preview उपलब्ध है; DevLys और अन्य profiles को अलग-अलग verify करना बाकी है.",
                12, MUTED, false
        );
        legacyInfo.setPadding(0, dp(7), 0, 0);
        legacyInfo.setLineSpacing(dp(2), 1f);
        legacyCard.addView(legacyInfo);

        Button converterInfo = button("Legacy conversion status", 0xFFE8F0EC, PRIMARY);
        LinearLayout.LayoutParams infoParams = wrapMatch();
        infoParams.topMargin = dp(12);
        legacyCard.addView(converterInfo, infoParams);
        converterInfo.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Legacy-to-Unicode conversion")
                .setMessage(
                        "Writer toolbar में Unicode चुनकर Kruti Dev 010 text को Unicode Hindi में convert करने का preview बना सकते हैं। "
                                + "कई DevLys 010 files similar map use करती हैं, लेकिन special glyphs अलग हो सकते हैं। "
                                + "Chanakya, Shusha, Shree Lipi और दूसरे versions के converters अभी शामिल नहीं हैं। "
                                + "Conversion के बाद output proofread करें; original file Save करने तक नहीं बदलती।"
                )
                .setPositiveButton("OK", null)
                .show());

        TextView footer = text(
                "Unicode text + Devanagari shaping + font coverage are separate requirements.",
                11, MUTED, false
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        root.addView(footer);
    }

    private void renderFonts() {
        if (fontList == null) return;
        fontList.removeAllViews();
        String current = FontManager.getDefaultFont(this);
        selectedStatus.setText("Default Writer font: " + current);

        List<FontManager.FontRecord> fonts = FontManager.getAvailableFonts(this);
        for (FontManager.FontRecord record : fonts) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(13), dp(14), dp(13));
            card.setBackground(roundRect(Color.WHITE, dp(13)));
            LinearLayout.LayoutParams cardParams = wrapMatch();
            cardParams.bottomMargin = dp(9);
            fontList.addView(card, cardParams);

            TextView name = text(record.name, 15, INK, true);
            card.addView(name);
            TextView category = text(record.category, 10, PRIMARY, true);
            category.setPadding(0, dp(2), 0, dp(7));
            card.addView(category);

            TextView sample = text(previewSample(), 16, INK, false);
            sample.setTypeface(FontManager.loadTypeface(this, record));
            sample.setLineSpacing(dp(2), 1f);
            sample.setPadding(0, 0, 0, dp(9));
            card.addView(sample);

            TextView description = text(record.description, 11, MUTED, false);
            description.setPadding(0, 0, 0, dp(9));
            card.addView(description);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(actions, wrapMatch());

            boolean isDefault = record.cssFamily.equals(current);
            Button use = button(isDefault ? "Selected default" : "Use in Writer",
                    isDefault ? 0xFFE3EFE9 : PRIMARY,
                    isDefault ? PRIMARY : Color.WHITE);
            actions.addView(use, new LinearLayout.LayoutParams(0, dp(42), 1f));
            use.setEnabled(!isDefault);
            use.setOnClickListener(v -> {
                FontManager.setDefaultFont(this, record);
                selectedStatus.setText("Default Writer font: " + record.cssFamily);
                Toast.makeText(this, record.name + " selected for Writer.", Toast.LENGTH_SHORT).show();
                renderFonts();
            });

            if (!record.bundled) {
                View spacer = new View(this);
                actions.addView(spacer, new LinearLayout.LayoutParams(dp(8), 1));
                Button remove = button("Remove", 0xFFF8E9E7, 0xFF9D3D32);
                actions.addView(remove, new LinearLayout.LayoutParams(dp(92), dp(42)));
                remove.setOnClickListener(v -> confirmRemove(record));
            }
        }
    }

    private String previewSample() {
        return previewInput == null ? FontManager.HINDI_SAMPLE + "\n" + FontManager.SHAPING_SAMPLE
                : previewInput.getText().toString();
    }

    private void chooseFontFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FONT);
        } catch (Exception exception) {
            Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_FONT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        String name = queryDisplayName(uri);
        selectedStatus.setText("Validating " + name + "…");
        importer.execute(() -> {
            try {
                File imported = FontManager.importFont(this, uri, name);
                runOnUiThread(() -> {
                    renderFonts();
                    Toast.makeText(this, "Font imported: " + imported.getName(),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    selectedStatus.setText("Font import failed.");
                    new AlertDialog.Builder(this)
                            .setTitle("Could not import font")
                            .setMessage(exception.getMessage() == null
                                    ? "The font file is invalid or unsupported." : exception.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "ImportedFont.ttf" : uri.getLastPathSegment();
    }

    private void confirmRemove(FontManager.FontRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Remove imported font?")
                .setMessage(record.name + " will be removed from this device's private font library.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    boolean wasDefault = FontManager.getDefaultFont(this).equals(record.cssFamily);
                    FontManager.removeCustomFont(record);
                    if (wasDefault) {
                        FontManager.setDefaultFont(this, FontManager.getBundledFonts().get(0));
                    }
                    renderFonts();
                })
                .show();
    }

    private void addHeading(LinearLayout parent, String heading, String eyebrow) {
        TextView label = text(eyebrow, 9, PRIMARY, true);
        label.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams labelParams = wrapMatch();
        labelParams.topMargin = dp(22);
        labelParams.bottomMargin = dp(3);
        parent.addView(label, labelParams);
        parent.addView(text(heading, 18, INK, true), wrapMatch());
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setBackground(roundRect(background, dp(11)));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setMinHeight(dp(42));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams wrapMatch() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        importer.shutdown();
        super.onDestroy();
    }
}
