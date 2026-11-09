package com.fz.office.writer;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.fz.office.fonts.FontManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline DOCX editor opened only after the user taps Edit in the viewer. */
public class WriterEditorActivity extends Activity {
    public static final String EXTRA_DOCUMENT_URI = "document_uri";
    public static final String EXTRA_DOCUMENT_NAME = "document_name";
    private static final int REQUEST_CREATE_COPY = 4101;
    private static final int MAX_DOCX_BYTES = 80 * 1024 * 1024;
    private static String preloadedUri;
    private static DocxCodec.LoadedDocument preloadedDocument;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** Transfers the already-parsed viewer document into Edit without a second ZIP read. */
    public static synchronized void setPreloadedDocument(
            Uri uri, DocxCodec.LoadedDocument document) {
        preloadedUri = uri == null ? null : uri.toString();
        preloadedDocument = document;
    }

    private static synchronized DocxCodec.LoadedDocument takePreloadedDocument(Uri uri) {
        String requested = uri == null ? null : uri.toString();
        if (requested == null || !requested.equals(preloadedUri)) return null;
        DocxCodec.LoadedDocument document = preloadedDocument;
        preloadedUri = null;
        preloadedDocument = null;
        return document;
    }
    private Uri source;
    private String name = "Document.docx";
    private String html = "<p></p>";
    private Map<String, byte[]> packageEntries;
    private List<String> documentFonts = new ArrayList<>();
    private DocxCodec.PageLayout layout;
    private byte[] pendingBytes;
    private WebView web;
    private TextView status;
    private boolean ready, dirty, saving, initializationStarted;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String value = getIntent().getStringExtra(EXTRA_DOCUMENT_URI);
        if (value != null) source = Uri.parse(value);
        String supplied = getIntent().getStringExtra(EXTRA_DOCUMENT_NAME);
        if (supplied != null && !supplied.trim().isEmpty()) name = supplied;
        else if (source != null) name = displayName(source);
        if (source == null) { finish(); return; }
        buildUi();
        load();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(0xFFF4F7F5);
        getWindow().setNavigationBarColor(0xFFF4F7F5);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFEEF3F0);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(10), dp(6), dp(12), dp(6));
        head.setBackgroundColor(Color.WHITE);
        root.addView(head, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView back = text("‹", 25, 0xFF1D5145);
        back.setGravity(Gravity.CENTER);
        head.addView(back, new LinearLayout.LayoutParams(dp(40), -1));
        back.setOnClickListener(v -> closeEditor());
        TextView title = text("FZ Writer", 15, 0xFF18352D);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = text("Save copy", 13, Color.WHITE);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(12), 0, dp(12), 0);
        save.setBackground(round(0xFF176B5B, 18));
        head.addView(save, new LinearLayout.LayoutParams(-2, dp(38)));
        save.setOnClickListener(v -> save());

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(5), dp(8), dp(7));
        scroll.addView(bar, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(48)));
        add(bar,"B","bold",null); add(bar,"I","italic",null); add(bar,"U","underline",null);
        add(bar,"H1","formatBlock","h1"); add(bar,"H2","formatBlock","h2"); add(bar,"¶","formatBlock","p");
        add(bar,"• List","insertUnorderedList",null); add(bar,"1. List","insertOrderedList",null);
        add(bar,"Left","justifyLeft",null); add(bar,"Center","justifyCenter",null);
        add(bar,"Right","justifyRight",null); add(bar,"Table","insertTable",null);
        add(bar,"Undo","undo",null); add(bar,"Redo","redo",null);

        web = new WebView(this);
        web.setBackgroundColor(0xFFEEF3F0);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("file:///android_asset/writer/");
            }
            @Override public void onPageFinished(WebView view, String url) {
                ready = true; initialize();
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        status = text("Loading document…", 10, 0xFF60766D);
        status.setPadding(dp(13), dp(8), dp(13), dp(8));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
        web.loadUrl("file:///android_asset/writer/editor.html");
    }

    private void add(LinearLayout bar, String label, String command, String value) {
        TextView b = text(label, 12, 0xFF23483D);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(11), 0, dp(11), 0);
        b.setBackground(round(0xFFF0F5F2, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
        lp.rightMargin = dp(6); bar.addView(b, lp);
        b.setOnClickListener(v -> {
            String js = "insertTable".equals(command) ? "FZWriter.insertTable()"
                    : "FZWriter.command(" + org.json.JSONObject.quote(command) + ","
                    + (value == null ? "null" : org.json.JSONObject.quote(value)) + ")";
            run(js);
        });
    }

    private void load() {
        io.execute(() -> {
            try {
                // The Viewer already parsed this exact DOCX before the user tapped Edit.
                // Reuse its package entries and parsed HTML instead of opening and parsing it twice.
                DocxCodec.LoadedDocument doc = takePreloadedDocument(source);
                if (doc == null) {
                    try (InputStream in = getContentResolver().openInputStream(source)) {
                        if (in == null) throw new IOException("Could not read selected document.");
                        doc = DocxCodec.readDocx(
                                new java.io.ByteArrayInputStream(readLimited(in)));
                    }
                }
                if (doc == null) throw new IOException("Could not read selected document.");
                // Unicode DOCX files use the APK-bundled fonts directly. Import the
                // larger legacy pack only when the document declares a legacy Hindi font.
                if (doc.legacyFontName != null && !doc.legacyFontName.trim().isEmpty()) {
                    try { FontManager.importBundledFontArchives(this); } catch (Exception ignored) { }
                }
                List<String> loadedFonts = new ArrayList<>(doc.fontNames);
                if (doc.html != null && doc.html.toLowerCase(Locale.ROOT).contains("<img")) {
                    throw new IOException("This document contains embedded images. Editing is disabled to protect them from being lost.");
                }
                html = doc.html == null || doc.html.trim().isEmpty() ? "<p></p>" : doc.html;
                packageEntries = doc.packageEntries;
                layout = doc.pageLayout;
                runOnUiThread(() -> {
                    documentFonts = loadedFonts;
                    status.setText("Document read · Preparing editor…");
                    initialize();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Cannot edit this document")
                        .setMessage(e.getMessage() == null ? "Unsupported DOCX content." : e.getMessage())
                        .setPositiveButton("OK", (d,w) -> finish()).show());
            }
        });
    }

    private void initialize() {
        if (!ready || web == null || packageEntries == null || initializationStarted) return;
        // onPageFinished and the JS-ready bridge can both reach this method.
        // Initialize the document only once to avoid rebuilding its full DOM twice.
        initializationStarted = true;

        // Font discovery, reading imported TTF/OTF files, Base64 encoding and quoting
        // a large DOCX HTML payload can all be expensive. Keep that work off the UI
        // thread so the Activity remains responsive while the editor is prepared.
        final String sourceHtml = html;
        final List<String> sourceFonts = new ArrayList<>(documentFonts);
        final DocxCodec.PageLayout sourceLayout = layout;
        if (status != null) status.setText("Document read · Preparing fonts…");

        io.execute(() -> {
            try {
                String family = FontManager.getDefaultFont(this);
                StringBuilder css = new StringBuilder(FontManager.getBundledCss());
                try {
                    FontManager.FontRecord selected = FontManager.findByFamily(this, family);
                    if (selected != null && !selected.bundled) {
                        css.append(FontManager.getCustomFontCss(selected));
                    }
                } catch (Exception ignored) { }

                Set<String> registered = new HashSet<>();
                for (String documentFont : sourceFonts) {
                    if (documentFont == null || documentFont.trim().isEmpty()) continue;
                    String key = documentFont.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if (key.isEmpty() || !registered.add(key)) continue;
                    try {
                        css.append(FontManager.getCustomFontCssForFamily(this, documentFont));
                    } catch (Exception ignored) { }
                }

                int pageWidth = sourceLayout == null ? 11906 : sourceLayout.widthTwips;
                int pageHeight = sourceLayout == null ? 16838 : sourceLayout.heightTwips;
                int marginTop = sourceLayout == null ? 1440 : sourceLayout.topTwips;
                int marginRight = sourceLayout == null ? 1440 : sourceLayout.rightTwips;
                int marginBottom = sourceLayout == null ? 1440 : sourceLayout.bottomTwips;
                int marginLeft = sourceLayout == null ? 1440 : sourceLayout.leftTwips;

                // Build the potentially multi-megabyte JavaScript argument away from
                // the main thread as well; WebView evaluation itself stays on UI.
                String js = "FZWriter.registerFonts(" + org.json.JSONObject.quote(css.toString()) + ");"
                        + "FZWriter.setPageLayout(" + pageWidth + "," + pageHeight + ","
                        + marginTop + "," + marginRight + "," + marginBottom + "," + marginLeft + ");"
                        + "FZWriter.setFont(" + org.json.JSONObject.quote(family) + ",false);"
                        + "FZWriter.setHtml(" + org.json.JSONObject.quote(sourceHtml) + ");";

                runOnUiThread(() -> {
                    if (isFinishing() || web == null) return;
                    web.evaluateJavascript(js, null);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    initializationStarted = false;
                    if (status != null) status.setText("Could not prepare editor");
                    new AlertDialog.Builder(this)
                            .setTitle("Cannot prepare editor")
                            .setMessage(exception.getMessage() == null
                                    ? "The document could not be prepared for editing."
                                    : exception.getMessage())
                            .setPositiveButton("OK", (dialog, which) -> finish())
                            .show();
                });
            }
        });
    }

    private void run(String js) {
        if (ready && packageEntries != null) web.evaluateJavascript(js, null);
    }

    private void save() {
        if (saving || packageEntries == null) return;
        if (!dirty) {
            new AlertDialog.Builder(this).setMessage("No changes to save.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        run("FZWriter.requestSave()");
    }

    private void prepare(String editedHtml) {
        if (saving || packageEntries == null) return;
        saving = true; status.setText("Preparing edited copy…");
        io.execute(() -> {
            try {
                byte[] bytes = DocxCodec.writeDocx(editedHtml, packageEntries, layout);
                runOnUiThread(() -> { pendingBytes = bytes; saving = false; pickSaveLocation(); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    saving = false; status.setText("Could not prepare document");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void pickSaveLocation() {
        if (pendingBytes == null) return;
        String base = name.toLowerCase(Locale.ROOT).endsWith(".docx")
                ? name.substring(0, name.length() - 5) : name;
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        create.putExtra(Intent.EXTRA_TITLE, base + " - edited.docx");
        startActivityForResult(create, REQUEST_CREATE_COPY);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_CREATE_COPY) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            pendingBytes = null; status.setText("Save cancelled · Original unchanged"); return;
        }
        Uri target = data.getData();
        byte[] bytes = pendingBytes; pendingBytes = null;
        status.setText("Saving copy…");
        io.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(target, "w")) {
                if (out == null) throw new IOException("Could not create selected file.");
                out.write(bytes); out.flush();
                runOnUiThread(() -> {
                    dirty = false;
                    Toast.makeText(this, "Edited DOCX copy saved", Toast.LENGTH_LONG).show();
                    Intent open = new Intent(this, WriterActivity.class);
                    open.putExtra(WriterActivity.EXTRA_DOCUMENT_URI, target.toString());
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(open); finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Save failed");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void closeEditor() {
        if (!dirty) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("Discard unsaved changes?")
                .setMessage("Your original document has not been changed.")
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Discard", (d,w) -> finish()).show();
    }
    @Override public void onBackPressed() { closeEditor(); }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) return c.getString(i);
            }
        } catch (Exception ignored) { }
        return "Document.docx";
    }

    private byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n, total = 0;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > MAX_DOCX_BYTES) throw new IOException("DOCX file exceeds the supported size.");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size);
        v.setTextColor(color); return v;
    }
    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.removeJavascriptInterface("AndroidBridge"); web.destroy(); web = null; }
        io.shutdown(); super.onDestroy();
    }

    private final class Bridge {
        @JavascriptInterface public void onEditorReady() { runOnUiThread(() -> initialize()); }
        @JavascriptInterface public void onLayoutReady() {
            runOnUiThread(() -> {
                if (!isFinishing() && status != null) status.setText("Editing a copy · Ready");
            });
        }
        @JavascriptInterface public void onDirty() {
            runOnUiThread(() -> { dirty = true; if (status != null) status.setText("Unsaved changes · Save copy to keep them"); });
        }
        @JavascriptInterface public void onSaveHtml(String value) {
            runOnUiThread(() -> prepare(value == null ? "<p></p>" : value));
        }
    }
}
