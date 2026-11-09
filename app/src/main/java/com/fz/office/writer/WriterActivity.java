package com.fz.office.writer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import com.fz.office.fonts.FontManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only document viewer.
 *
 * Legacy conversion is performed in the WebView presentation layer only.
 * No document write stream or Save action is exposed by this activity.
 */
public class WriterActivity extends Activity {
    public static final String EXTRA_DOCUMENT_URI = "document_uri";
    private static final int MAX_TEXT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DOCX_BYTES = 80 * 1024 * 1024;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private Uri sourceUri;
    private String documentName = "Document";
    private boolean plainText;
    private boolean pageReady;
    private boolean viewerInitialized;
    private boolean documentLoaded;

    private String pendingHtml = "<p></p>";
    private String pendingLegacyProfile = "";
    private String pendingLegacyFontName = "";
    private boolean pendingLegacyTextHint;
    private boolean pendingLegacyFontLoaded;
    private final Set<String> registeredDocumentFonts = new HashSet<>();
    private List<String> pendingDocumentFonts = new ArrayList<>();
    private DocxCodec.PageLayout pendingPageLayout;
    // Keep the parsed DOCX package while the read-only viewer is open so Edit
    // can reuse it instead of reading and parsing the same source a second time.
    private DocxCodec.LoadedDocument loadedDocumentForEdit;

    private WebView viewer;
    private TextView titleView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String uriValue = getIntent().getStringExtra(EXTRA_DOCUMENT_URI);
        if (uriValue != null && !uriValue.isEmpty()) {
            sourceUri = Uri.parse(uriValue);
            documentName = queryDisplayName(sourceUri);
        }
        plainText = documentName.toLowerCase(Locale.ROOT).endsWith(".txt")
                || "text/plain".equalsIgnoreCase(sourceUri == null ? null : getContentResolver().getType(sourceUri));
        buildViewerScreen();
        loadDocument();
    }

    private void buildViewerScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF1F4F2);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(7), dp(10), dp(7));
        top.setBackgroundColor(0xFFFFFFFF);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        TextView back = control("‹", 27, 0xFF1D5145);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);
        titleColumn.setPadding(dp(7), 0, dp(5), 0);
        top.addView(titleColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        titleView = control(documentName, 14, 0xFF18352D);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titleColumn.addView(titleView);

        TextView viewOnly = control("VIEW ONLY", 9, 0xFF176B5B);
        viewOnly.setLetterSpacing(0.12f);
        titleColumn.addView(viewOnly);

        if (!plainText && documentName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            TextView edit = control("Edit", 13, 0xFFFFFFFF);
            edit.setGravity(Gravity.CENTER);
            edit.setPadding(dp(10), 0, dp(10), 0);
            edit.setBackgroundColor(0xFF176B5B);
            top.addView(edit, new LinearLayout.LayoutParams(-2, dp(36)));
            edit.setOnClickListener(v -> openEditor());
        }

        TextView zoomOut = control("A−", 15, 0xFF1D5145);
        zoomOut.setGravity(Gravity.CENTER);
        top.addView(zoomOut, new LinearLayout.LayoutParams(dp(42), dp(42)));
        zoomOut.setOnClickListener(v -> runViewerScript("FZViewer.zoom(-1)"));

        TextView zoomIn = control("A+", 15, 0xFF1D5145);
        zoomIn.setGravity(Gravity.CENTER);
        top.addView(zoomIn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        zoomIn.setOnClickListener(v -> runViewerScript("FZViewer.zoom(1)"));
        viewer = new WebView(this);
        viewer.setBackgroundColor(0xFFF1F4F2);

        WebSettings settings = viewer.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        viewer.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                initializeViewer();
            }
        });

        root.addView(viewer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        statusView = control("Read only · Original document is never changed", 10, 0xFF60766D);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setPadding(dp(13), dp(8), dp(13), dp(8));
        statusView.setBackgroundColor(0xFFFFFFFF);
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        applySafeAreaInsets(root);
        viewer.loadUrl("file:///android_asset/writer/viewer.html");
    }

    private void loadDocument() {
        if (sourceUri == null) {
            showOpenError("No document was selected.");
            return;
        }
        statusView.setText("Opening document…");
        ioExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(sourceUri)) {
                if (input == null) throw new IOException("Could not read the selected document.");
                byte[] data = readLimited(input, plainText ? MAX_TEXT_BYTES : MAX_DOCX_BYTES);

                String html;
                String legacyProfile = "";
                String legacyFontName = "";
                boolean legacyTextHint = false;
                DocxCodec.PageLayout pageLayout = null;
                DocxCodec.LoadedDocument parsedDocument = null;
                List<String> documentFonts = new ArrayList<>();

                if (plainText) {
                    String text = new String(data, StandardCharsets.UTF_8);
                    if (text.startsWith("\uFEFF")) text = text.substring(1);
                    html = DocxCodec.plainTextToHtml(text);
                } else if (documentName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                    DocxCodec.LoadedDocument loaded =
                            DocxCodec.readDocx(new ByteArrayInputStream(data));
                    parsedDocument = loaded;
                    html = loaded.html;
                    legacyProfile = loaded.legacyProfile;
                    legacyFontName = loaded.legacyFontName;
                    legacyTextHint = DocxCodec.looksLikeLegacyHindi(html);
                    pageLayout = loaded.pageLayout;
                    documentFonts.addAll(loaded.fontNames);
                } else {
                    throw new IOException("This viewer currently supports DOCX and TXT files.");
                }

                // The legacy font archive is large; ordinary Unicode DOCX files never need it.
                // Install it only when this document explicitly declares a legacy Hindi family.
                if (!legacyFontName.isEmpty()) {
                    try { FontManager.importBundledFontArchives(WriterActivity.this); }
                    catch (Exception ignored) { }
                }

                final String loadedHtml = html;
                final String loadedProfile = legacyProfile;
                final String loadedFontName = legacyFontName;
                final boolean loadedHint = legacyTextHint;
                final DocxCodec.PageLayout loadedPageLayout = pageLayout;
                final DocxCodec.LoadedDocument loadedDocx = parsedDocument;
                final List<String> loadedFonts = new ArrayList<>(documentFonts);

                runOnUiThread(() -> {
                    pendingHtml = loadedHtml;
                    documentLoaded = true;
                    pendingLegacyProfile = loadedProfile;
                    pendingLegacyFontName = loadedFontName;
                    pendingLegacyTextHint = loadedHint;
                    pendingPageLayout = loadedPageLayout;
                    loadedDocumentForEdit = loadedDocx;
                    pendingDocumentFonts = loadedFonts;
                    if (titleView != null) titleView.setText(documentName);
                    updateStatus();
                    applyPendingDocument();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showOpenError(exception.getMessage() == null
                        ? "The file may be damaged or use unsupported document features."
                        : exception.getMessage()));
            }
        });
    }

    private void initializeViewer() {
        if (!pageReady || viewer == null || viewerInitialized) return;
        viewerInitialized = true;
        try {
            String css = FontManager.getBundledCss();
            String family = FontManager.getDefaultFont(this);
            String script = "FZViewer.registerFonts(" + JSONObject.quote(css) + ");"
                    + "FZViewer.setDefaultFont(" + JSONObject.quote(family) + ");";
            viewer.evaluateJavascript(script, null);

            FontManager.FontRecord selected = FontManager.findByFamily(this, family);
            if (!selected.bundled) {
                String customCss = FontManager.getCustomFontCss(selected);
                if (!customCss.isEmpty()) {
                    viewer.evaluateJavascript("FZViewer.registerFonts("
                            + JSONObject.quote(customCss) + ");", null);
                }
            }

            applyPendingDocument();
        } catch (Exception exception) {
            statusView.setText("Some fonts could not be registered · View only");
            applyPendingDocument();
        }
    }

    private void registerDocumentFonts() {
        if (!pageReady || viewer == null) return;
        pendingLegacyFontLoaded = false;
        List<String> requested = new ArrayList<>(pendingDocumentFonts);
        if (pendingLegacyFontName != null && !pendingLegacyFontName.isEmpty()) {
            requested.add(pendingLegacyFontName);
        }
        for (String documentFont : requested) {
            String key = documentFont.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (key.isEmpty() || registeredDocumentFonts.contains(key)) continue;
            try {
                String customCss = FontManager.getCustomFontCssForFamily(this, documentFont);
                if (!customCss.isEmpty()) {
                    viewer.evaluateJavascript("FZViewer.registerFonts("
                            + JSONObject.quote(customCss) + ");", null);
                    registeredDocumentFonts.add(key);
                    if (sameFontFamily(documentFont, pendingLegacyFontName)) {
                        pendingLegacyFontLoaded = true;
                    }
                }
            } catch (Exception ignored) { }
        }
    }

    private boolean sameFontFamily(String first, String second) {
        if (first == null || second == null) return false;
        return first.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
                .equals(second.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
    }

    private void applyPendingDocument() {
        if (!pageReady || viewer == null || !documentLoaded) return;
        registerDocumentFonts();
        updateStatus();
        JSONObject pageLayout = new JSONObject();
        try {
            DocxCodec.PageLayout layout = pendingPageLayout;
            if (layout != null) {
                pageLayout.put("widthTwips", layout.widthTwips);
                pageLayout.put("heightTwips", layout.heightTwips);
                pageLayout.put("topTwips", layout.topTwips);
                pageLayout.put("rightTwips", layout.rightTwips);
                pageLayout.put("bottomTwips", layout.bottomTwips);
                pageLayout.put("leftTwips", layout.leftTwips);
            }
        } catch (Exception ignored) { }
        String script = "FZViewer.setDocument("
                + JSONObject.quote(pendingHtml == null ? "<p></p>" : pendingHtml) + ","
                + JSONObject.quote(pendingLegacyProfile == null ? "" : pendingLegacyProfile) + ","
                + JSONObject.quote(pendingLegacyFontName == null ? "" : pendingLegacyFontName) + ","
                + pendingLegacyTextHint + ","
                + pendingLegacyFontLoaded + "," + pageLayout.toString() + ");";
        viewer.evaluateJavascript(script, null);
    }

    private void updateStatus() {
        if (pendingLegacyFontLoaded && !pendingLegacyFontName.isEmpty()) {
            statusView.setText("Matching font loaded: " + pendingLegacyFontName
                    + " · Original DOCX text preserved");
        } else if (!pendingLegacyFontName.isEmpty()) {
            statusView.setText("Missing font: " + pendingLegacyFontName
                    + " · Import it in Hindi Font Center");
        } else if (pendingLegacyTextHint) {
            statusView.setText("Possible legacy Hindi text · No verified font name");
        } else if (plainText) {
            statusView.setText("UTF-8 text · View only · Original file unchanged");
        } else {
            statusView.setText("DOCX viewer · Original file is never changed");
        }
    }

    private void openEditor() {
        if (!documentLoaded) {
            Toast.makeText(this, "Please wait until the document finishes loading.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sourceUri == null || plainText) return;
        if (pendingHtml != null && pendingHtml.toLowerCase(Locale.ROOT).contains("<img")) {
            new AlertDialog.Builder(this)
                    .setTitle("Editing not available for this document")
                    .setMessage("This DOCX contains embedded images. Editing is currently limited to text, formatting and tables, so the image content is protected from accidental loss.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (loadedDocumentForEdit == null) {
            Toast.makeText(this, "The document is not ready for editing yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        WriterEditorActivity.setPreloadedDocument(sourceUri, loadedDocumentForEdit);
        Intent intent = new Intent(this, WriterEditorActivity.class);
        intent.putExtra(WriterEditorActivity.EXTRA_DOCUMENT_URI, sourceUri.toString());
        intent.putExtra(WriterEditorActivity.EXTRA_DOCUMENT_NAME, documentName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void runViewerScript(String script) {
        if (viewer != null && pageReady) viewer.evaluateJavascript(script, null);
    }

    private void showOpenError(String message) {
        if (statusView != null) statusView.setText("Could not open document");
        new AlertDialog.Builder(this)
                .setTitle("Document could not be opened")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .show();
    }

    private TextView control(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        return view;
    }

    private String queryDisplayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) { }
        if (name == null || name.trim().isEmpty()) {
            String last = uri.getLastPathSegment();
            name = last == null ? "Document" : last;
        }
        return name;
    }

    private byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("This file is larger than the supported limit.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        if (viewer != null) {
            viewer.stopLoading();
            viewer.destroy();
            viewer = null;
        }
        ioExecutor.shutdown();
        super.onDestroy();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
