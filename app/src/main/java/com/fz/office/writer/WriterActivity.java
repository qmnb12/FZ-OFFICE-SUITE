package com.fz.office.writer;

import android.app.Activity;
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
    private boolean documentFontsLoading;
    private boolean documentFontsResolved;

    private String pendingHtml = "<p></p>";
    private String pendingLegacyProfile = "";
    private String pendingLegacyFontName = "";
    private boolean pendingLegacyTextHint;
    private boolean pendingLegacyFontLoaded;
    private List<String> pendingDocumentFonts = new ArrayList<>();
    private DocxCodec.PageLayout pendingPageLayout;

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
                List<String> documentFonts = new ArrayList<>();

                if (plainText) {
                    String text = new String(data, StandardCharsets.UTF_8);
                    if (text.startsWith("\uFEFF")) text = text.substring(1);
                    html = DocxCodec.plainTextToHtml(text);
                } else if (documentName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                    DocxCodec.LoadedDocument loaded =
                            DocxCodec.readDocx(new ByteArrayInputStream(data));
                    html = loaded.html;
                    legacyProfile = loaded.legacyProfile;
                    legacyFontName = loaded.legacyFontName;
                    legacyTextHint = DocxCodec.looksLikeLegacyHindi(html);
                    pageLayout = loaded.pageLayout;
                    documentFonts.addAll(loaded.fontNames);
                } else {
                    throw new IOException("This viewer currently supports DOCX and TXT files.");
                }

                // Legacy fonts are installed asynchronously after the viewer has displayed
                // the first layout, so a large ZIP import never blocks the document's first render.

                final String loadedHtml = html;
                final String loadedProfile = legacyProfile;
                final String loadedFontName = legacyFontName;
                final boolean loadedHint = legacyTextHint;
                final DocxCodec.PageLayout loadedPageLayout = pageLayout;
                final List<String> loadedFonts = new ArrayList<>(documentFonts);

                runOnUiThread(() -> {
                    pendingHtml = loadedHtml;
                    documentLoaded = true;
                    pendingLegacyProfile = loadedProfile;
                    pendingLegacyFontName = loadedFontName;
                    pendingLegacyTextHint = loadedHint;
                    pendingPageLayout = loadedPageLayout;
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
            // Custom font directory scans and Base64 encoding are deferred to the
            // same IO-thread pass as DOCX font registration below.
            applyPendingDocument();
        } catch (Exception exception) {
            statusView.setText("Some fonts could not be registered · View only");
            applyPendingDocument();
        }
    }

    private void registerDocumentFontsAsync() {
        if (!pageReady || viewer == null || !documentLoaded
                || documentFontsLoading || documentFontsResolved) return;
        documentFontsLoading = true;

        final List<String> requested = new ArrayList<>(pendingDocumentFonts);
        final String defaultFamily = FontManager.getDefaultFont(this);
        if (defaultFamily != null && !defaultFamily.trim().isEmpty()) requested.add(defaultFamily);
        final String legacyName = pendingLegacyFontName;
        if (legacyName != null && !legacyName.trim().isEmpty()) requested.add(legacyName);

        ioExecutor.execute(() -> {
            String customCss = "";
            boolean legacyLoaded = false;
            try {
                if (legacyName != null && !legacyName.trim().isEmpty()) {
                    FontManager.importBundledFontArchives(WriterActivity.this);
                }
                customCss = FontManager.getCustomFontCssForFamilies(WriterActivity.this, requested);
                if (legacyName != null && !legacyName.trim().isEmpty()) {
                    String declaration = "font-family:" + JSONObject.quote(legacyName) + ";";
                    legacyLoaded = customCss.contains(declaration);
                }
            } catch (Exception ignored) { }

            final String loadedCss = customCss;
            final boolean loadedLegacy = legacyLoaded;
            runOnUiThread(() -> {
                documentFontsLoading = false;
                documentFontsResolved = true;
                pendingLegacyFontLoaded = loadedLegacy;
                if (isFinishing() || viewer == null) return;
                if (!loadedCss.isEmpty()) {
                    viewer.evaluateJavascript("FZViewer.registerFonts("
                            + JSONObject.quote(loadedCss) + ");", null);
                }
                // Reapply once after fonts are registered so the viewer uses the exact
                // document font instead of keeping the fallback face from first paint.
                applyPendingDocument();
                updateStatus();
            });
        });
    }

    private void applyPendingDocument() {
        if (!pageReady || viewer == null || !documentLoaded) return;
        registerDocumentFontsAsync();
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
        if (documentFontsLoading && pendingLegacyFontName != null && !pendingLegacyFontName.isEmpty()) {
            statusView.setText("Loading matching font: " + pendingLegacyFontName);
        } else if (pendingLegacyFontLoaded && !pendingLegacyFontName.isEmpty()) {
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
