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
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
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
    private static final int REQUEST_IMPORT_ARCHIVE = 411;
    private static final int INITIAL_FONT_LIMIT = 36;
    private int visibleFontLimit = INITIAL_FONT_LIMIT;
    private EditText searchInput;
    private TextView countInfo;
    private Button loadMoreButton;
    private final ExecutorService importer = Executors.newSingleThreadExecutor();
    private LinearLayout fontList;
    private TextView previewInput;
    private TextView selectedStatus;
    // Reuse the scanned library while the user filters/searches; refresh only after changes.
    private List<FontManager.FontRecord> fontSnapshot;

    private static final int INK = Color.rgb(26, 50, 42);
    private static final int MUTED = Color.rgb(101, 119, 109);
    private static final int PRIMARY = Color.rgb(23, 107, 91);
    private static final int BACKGROUND = Color.rgb(244, 247, 245);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        renderFonts();
        importer.execute(() -> {
            String setupMessage;
            try {
                FontManager.ImportResult legacy = FontManager.importBundledFontArchives(FontCenterActivity.this);
                setupMessage = "Font library ready · " + legacy.imported + " legacy fonts newly added.";
            } catch (Exception exception) {
                setupMessage = "Font import warning: " + (exception.getMessage() == null
                        ? "Some bundled fonts could not be imported." : exception.getMessage());
            }
            final String resultMessage = setupMessage;
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    fontSnapshot = null;
                    renderFonts();
                    selectedStatus.setText(resultMessage);
                }
            });
        });
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
                "12 Unicode fonts के साथ DevLys, Kruti Dev और Mangal legacy fonts पहली बार अपने-आप install होते हैं। आप अतिरिक्त TTF/OTF या ZIP packs भी import कर सकते हैं। "
                        + "Fonts केवल इस device के private storage में रहेंगे।",
                13, MUTED, false
        );
        intro.setLineSpacing(dp(3), 1f);
        root.addView(intro);

        Button importButton = button("+  Import TTF / OTF", PRIMARY, Color.WHITE);
        LinearLayout.LayoutParams importParams = wrapMatch();
        importParams.topMargin = dp(15);
        root.addView(importButton, importParams);
        importButton.setOnClickListener(v -> chooseFontFile());

        Button archiveButton = button("+  Import ZIP font pack", 0xFFE3EFE9, PRIMARY);
        LinearLayout.LayoutParams archiveParams = wrapMatch();
        archiveParams.topMargin = dp(8);
        root.addView(archiveButton, archiveParams);
        archiveButton.setOnClickListener(v -> chooseFontArchive());

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setTextColor(INK);
        searchInput.setHint("Search all fonts: family, style or file name");
        searchInput.setPadding(dp(13), dp(10), dp(13), dp(10));
        searchInput.setBackground(roundRect(Color.WHITE, dp(11)));
        LinearLayout.LayoutParams searchParams = wrapMatch();
        searchParams.topMargin = dp(12);
        root.addView(searchInput, searchParams);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                visibleFontLimit = INITIAL_FONT_LIMIT;
                renderFonts();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

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

        addHeading(root, "Available font library", "UNICODE + LEGACY");
        TextView bundledInfo = text(
                "Bundled Unicode fonts are open-license. Imported Kruti Dev, DevLys and Mangal files are stored locally; their source ZIPs did not include redistribution licences, so they are not uploaded to the public repository.",
                12, MUTED, false
        );
        bundledInfo.setPadding(0, 0, 0, dp(10));
        root.addView(bundledInfo);

        fontList = new LinearLayout(this);
        fontList.setOrientation(LinearLayout.VERTICAL);
        root.addView(fontList, wrapMatch());
        loadMoreButton = button("Show more fonts", 0xFFE8F0EC, PRIMARY);
        LinearLayout.LayoutParams moreParams = wrapMatch();
        moreParams.topMargin = dp(6);
        root.addView(loadMoreButton, moreParams);
        loadMoreButton.setOnClickListener(v -> {
            visibleFontLimit += INITIAL_FONT_LIMIT;
            renderFonts();
        });

        addHeading(root, "Legacy Hindi fonts", "LOCAL FONT LIBRARY");
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
                "आपके ZIP packs से DevLys 010, Kruti Dev variants और Mangal fonts import किए जा सकते हैं। "
                        + "DOCX में matching legacy font मिलने पर viewer उस exact family को local font library से load करेगा; "
                        + "font import अपने-आप text encoding को Unicode में convert नहीं करता.",
                12, MUTED, false
        );
        legacyInfo.setPadding(0, dp(7), 0, 0);
        legacyInfo.setLineSpacing(dp(2), 1f);
        legacyCard.addView(legacyInfo);

        Button converterInfo = button("How legacy Hindi fonts render", 0xFFE8F0EC, PRIMARY);
        LinearLayout.LayoutParams infoParams = wrapMatch();
        infoParams.topMargin = dp(12);
        legacyCard.addView(converterInfo, infoParams);
        converterInfo.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Legacy Hindi font rendering")
                .setMessage(
                        "Legacy DOCX के लिए सबसे सही तरीका उसी exact font family/version को load करना है। "
                                + "DevLys 010, Kruti Dev और Mangal ZIP packs को Import ZIP font pack से private storage में जोड़ें। "
                                + "Viewer document में declared family को match करके original encoded characters को उसी font से render करेगा। "
                                + "Unicode conversion अलग tool है और document open होने पर automatic नहीं चलेगा।"
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
        selectedStatus.setText("Default font: " + current + " · Private font library");

        List<FontManager.FontRecord> allFonts = fontSnapshot;
        if (allFonts == null) {
            allFonts = FontManager.getAvailableFonts(this);
            fontSnapshot = allFonts;
        }
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        List<FontManager.FontRecord> filtered = new java.util.ArrayList<>();
        for (FontManager.FontRecord record : allFonts) {
            String haystack = (record.name + " " + record.category + " "
                    + record.cssFamily + " " + record.description).toLowerCase(java.util.Locale.ROOT);
            if (query.isEmpty() || haystack.contains(query)) filtered.add(record);
        }

        if (countInfo == null) {
            countInfo = text("", 11, MUTED, false);
            LinearLayout.LayoutParams countParams = wrapMatch();
            countParams.bottomMargin = dp(8);
            fontList.addView(countInfo, countParams);
        } else {
            fontList.addView(countInfo, wrapMatch());
        }
        int bundledCount = FontManager.getBundledFonts().size();
        int importedCount = Math.max(0, allFonts.size() - bundledCount);
        int visibleCount = Math.min(filtered.size(), visibleFontLimit);
        countInfo.setText("Total " + allFonts.size() + " font entries · Showing " + visibleCount
                + " of " + filtered.size() + " matches\n" + bundledCount + " bundled Unicode families · "
                + importedCount + " local/imported entries");

        int limit = Math.min(filtered.size(), visibleFontLimit);
        for (int index = 0; index < limit; index++) {
            FontManager.FontRecord record = filtered.get(index);
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

            TextView sample = text(previewSample(record), 16, INK, false);
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
            Button use = button(isDefault ? "Selected family" : "Use as display font",
                    isDefault ? 0xFFE3EFE9 : PRIMARY,
                    isDefault ? PRIMARY : Color.WHITE);
            actions.addView(use, new LinearLayout.LayoutParams(0, dp(42), 1f));
            use.setEnabled(!isDefault);
            use.setOnClickListener(v -> {
                FontManager.setDefaultFont(this, record);
                selectedStatus.setText("Default display font: " + record.cssFamily);
                Toast.makeText(this, record.name + " selected.", Toast.LENGTH_SHORT).show();
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

        if (loadMoreButton != null) {
            boolean more = filtered.size() > visibleFontLimit;
            loadMoreButton.setVisibility(more ? View.VISIBLE : View.GONE);
            loadMoreButton.setText("Show " + Math.min(INITIAL_FONT_LIMIT, filtered.size() - visibleFontLimit)
                    + " more fonts");
        }
    }

    private String previewSample(FontManager.FontRecord record) {
        String family = record.cssFamily.toLowerCase(java.util.Locale.ROOT);
        if (family.contains("devlys")) {
            return "Jheku 'kklu lfpo\nHkkjr jktLFkku\nfoHkkx vkSj izLrko";
        }
        if (family.contains("kruti dev") || family.contains("krutidev")) {
            return "Jheku 'kklu lfpo\nHkkjr jktLFkku\nfoHkkx vkSj izLrko";
        }
        return previewInput == null ? FontManager.HINDI_SAMPLE + "\n" + FontManager.SHAPING_SAMPLE
                : previewInput.getText().toString();
    }

    private void chooseFontArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_IMPORT_ARCHIVE);
        } catch (Exception exception) {
            Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show();
        }
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
        if ((requestCode != REQUEST_IMPORT_FONT && requestCode != REQUEST_IMPORT_ARCHIVE)
                || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        String name = queryDisplayName(uri);
        boolean archive = requestCode == REQUEST_IMPORT_ARCHIVE;
        selectedStatus.setText(archive ? "Scanning ZIP font pack " + name + "…" : "Validating " + name + "…");
        importer.execute(() -> {
            try {
                if (archive) {
                    FontManager.ImportResult result = FontManager.importFontArchive(this, uri);
                    runOnUiThread(() -> {
                        visibleFontLimit = INITIAL_FONT_LIMIT;
                        fontSnapshot = null;
                        renderFonts();
                        selectedStatus.setText("Imported " + result.imported + " fonts · "
                                + result.duplicates + " duplicates · " + result.invalid + " invalid");
                        new AlertDialog.Builder(this)
                                .setTitle("Font pack processed")
                                .setMessage("Imported: " + result.imported
                                        + "\nAlready present: " + result.duplicates
                                        + "\nInvalid/unreadable: " + result.invalid
                                        + "\nOther archive files ignored: " + result.ignored
                                        + "\n\nAll fonts are stored only in this app's private storage. "
                                        + "No files were uploaded or added to the public repository.")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                } else {
                    File imported = FontManager.importFont(this, uri, name);
                    runOnUiThread(() -> {
                        fontSnapshot = null;
                        renderFonts();
                        Toast.makeText(this, "Font imported: " + imported.getName(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    selectedStatus.setText("Font import failed.");
                    new AlertDialog.Builder(this)
                            .setTitle("Could not import font pack")
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
                    FontManager.removeCustomFont(this, record);
                    fontSnapshot = null;
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
