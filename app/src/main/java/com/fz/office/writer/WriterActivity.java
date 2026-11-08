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
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import com.fz.office.fonts.FontManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WriterActivity extends Activity {
    public static final String EXTRA_DOCUMENT_URI = "document_uri";
    private static final int REQUEST_SAVE_AS = 301;
    private static final int MAX_TEXT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DOCX_BYTES = 80 * 1024 * 1024;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private Uri sourceUri;
    private String documentName = "Untitled.docx";
    private boolean plainText;
    private boolean dirty;
    private boolean saving;
    private boolean pageReady;
    private boolean fontsInitialized;
    private String pendingHtml = "<p></p>";
    private byte[] pendingSaveBytes;
    private LinkedHashMap<String, byte[]> packageEntries = new LinkedHashMap<>();

    private WebView editor;
    private TextView titleView;
    private TextView statusView;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String uriValue = getIntent().getStringExtra(EXTRA_DOCUMENT_URI);
        if (uriValue != null && !uriValue.isEmpty()) {
            sourceUri = Uri.parse(uriValue);
            documentName = queryDisplayName(sourceUri);
        }
        plainText = documentName.toLowerCase().endsWith(".txt");

        buildEditorScreen();
        loadDocument();
    }

    private void buildEditorScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F7F5);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.setBackgroundColor(0xFFFFFFFF);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        Button back = makeButton("‹", 22);
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> handleBack());

        titleView = new TextView(this);
        titleView.setText(documentName);
        titleView.setTextColor(0xFF18352D);
        titleView.setTextSize(15);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titleView.setPadding(dp(8), 0, dp(8), 0);
        top.addView(titleView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        saveButton = makeButton("Save", 14);
        saveButton.setTextColor(0xFFFFFFFF);
        saveButton.setBackground(roundRect(0xFF176B5B, dp(12)));
        top.addView(saveButton, new LinearLayout.LayoutParams(dp(76), dp(40)));
        saveButton.setOnClickListener(v -> requestSave());

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolsScroll.setBackgroundColor(0xFFFFFFFF);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(8), dp(7), dp(8), dp(7));
        toolsScroll.addView(tools);
        root.addView(toolsScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        addTool(tools, "B", "bold");
        addTool(tools, "I", "italic");
        addTool(tools, "U", "underline");
        addTool(tools, "H1", "formatBlock", "h1");
        addTool(tools, "H2", "formatBlock", "h2");
        addTool(tools, "• List", "insertUnorderedList");
        addTool(tools, "1. List", "insertOrderedList");
        addTool(tools, "Left", "justifyLeft");
        addTool(tools, "Center", "justifyCenter");
        addTool(tools, "Right", "justifyRight");
        addTool(tools, "Undo", "undo");
        addTool(tools, "Redo", "redo");
        addTool(tools, "A+", "fontSize", "5");
        addTool(tools, "A−", "fontSize", "3");
        addTool(tools, "Font", "font");
        addTool(tools, "अक्षर", "hindiChars");
        addTool(tools, "Green", "foreColor", "#176B5B");
        addTool(tools, "Highlight", "backColor", "#FFF2A8");
        addTool(tools, "Table", "insertTable");

        editor = new WebView(this);
        editor.setBackgroundColor(0xFFF4F7F5);
        WebSettings settings = editor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        editor.addJavascriptInterface(new EditorBridge(), "AndroidBridge");
        editor.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                initializeEditor();
            }
        });
        root.addView(editor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        statusView = new TextView(this);
        statusView.setText("Offline Writer  ·  Hindi fonts  ·  DOCX / TXT");
        statusView.setTextColor(0xFF60766D);
        statusView.setTextSize(11);
        statusView.setPadding(dp(14), dp(8), dp(14), dp(8));
        statusView.setBackgroundColor(0xFFFFFFFF);
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        editor.loadUrl("file:///android_asset/writer/editor.html");
    }

    private void addTool(LinearLayout parent, String label, String command) {
        addTool(parent, label, command, null);
    }

    private void addTool(LinearLayout parent, String label, String command, String value) {
        Button button = makeButton(label, 12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.rightMargin = dp(6);
        parent.addView(button, params);
        button.setOnClickListener(v -> {
            String script;
            if ("insertTable".equals(command)) {
                script = "FZWriter.insertTable()";
            } else if ("formatBlock".equals(command)) {
                script = "FZWriter.command('formatBlock','" + value + "')";
            } else if ("font".equals(command)) {
                showFontPicker();
                return;
            } else if ("hindiChars".equals(command)) {
                showHindiCharacters();
                return;
            } else if ("fontSize".equals(command) || value != null) {
                script = "FZWriter.command('" + command + "','" + value + "')";
            } else {
                script = "FZWriter.command('" + command + "')";
            }
            runEditorScript(script);
        });
    }

    private Button makeButton(String label, int textSize) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(textSize);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextColor(0xFF1D5145);
        button.setBackground(roundRect(0xFFE8F0EC, dp(10)));
        return button;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void runEditorScript(String script) {
        if (editor != null && pageReady) {
            editor.evaluateJavascript(script, null);
        }
    }

    private void loadDocument() {
        if (sourceUri == null) {
            pendingHtml = "<p></p>";
            updateTitle("Untitled.docx");
            return;
        }
        statusView.setText("Opening document…");
        ioExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(sourceUri)) {
                if (input == null) throw new IOException("Could not read the selected document.");
                byte[] data = readLimited(input, plainText ? MAX_TEXT_BYTES : MAX_DOCX_BYTES);
                String html;
                if (plainText) {
                    String text = new String(data, StandardCharsets.UTF_8);
                    if (text.startsWith("\uFEFF")) text = text.substring(1);
                    html = DocxCodec.plainTextToHtml(text);
                } else if (documentName.toLowerCase().endsWith(".docx")) {
                    DocxCodec.LoadedDocument loaded =
                            DocxCodec.readDocx(new ByteArrayInputStream(data));
                    packageEntries = loaded.packageEntries;
                    html = loaded.html;
                } else {
                    throw new IOException("This Writer build opens .docx and .txt files only.");
                }
                final String loadedHtml = html;
                runOnUiThread(() -> {
                    pendingHtml = loadedHtml;
                    applyPendingHtml();
                    setDirty(false);
                    statusView.setText(plainText
                            ? "TXT document  ·  Offline editing"
                            : "DOCX document  ·  Basic formatting and tables");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    statusView.setText("Could not open this document.");
                    new AlertDialog.Builder(this)
                            .setTitle("Document could not be opened")
                            .setMessage(exception.getMessage() == null
                                    ? "The file may be damaged or use unsupported features."
                                    : exception.getMessage())
                            .setPositiveButton("OK", (dialog, which) -> finish())
                            .show();
                });
            }
        });
    }

    private void initializeEditor() {
        if (!pageReady || editor == null || fontsInitialized) return;
        fontsInitialized = true;
        try {
            String css = FontManager.getBundledCss();
            String family = FontManager.getDefaultFont(this);
            String script = "FZWriter.registerFonts(" + JSONObject.quote(css) + ");"
                    + "FZWriter.setFont(" + JSONObject.quote(family) + ",false);";
            editor.evaluateJavascript(script, null);
            FontManager.FontRecord selected = FontManager.findByFamily(this, family);
            if (!selected.bundled) registerCustomFont(selected);
            applyPendingHtml();
        } catch (Exception exception) {
            fontsInitialized = false;
            statusView.setText("Hindi font setup failed; using device fallback.");
            applyPendingHtml();
        }
    }

    private void registerCustomFont(FontManager.FontRecord record) {
        try {
            String css = FontManager.getCustomFontCss(record);
            if (!css.isEmpty()) {
                String script = "FZWriter.registerFonts(" + JSONObject.quote(css) + ");";
                editor.evaluateJavascript(script, null);
            }
        } catch (Exception exception) {
            Toast.makeText(this, "Could not load the imported font.", Toast.LENGTH_LONG).show();
        }
    }

    private void showFontPicker() {
        List<FontManager.FontRecord> fonts = FontManager.getAvailableFonts(this);
        String[] labels = new String[fonts.size()];
        for (int i = 0; i < fonts.size(); i++) {
            FontManager.FontRecord font = fonts.get(i);
            labels[i] = font.name + "  ·  " + font.category;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a Devanagari font")
                .setItems(labels, (dialog, which) -> {
                    FontManager.FontRecord selected = fonts.get(which);
                    FontManager.setDefaultFont(this, selected);
                    if (!selected.bundled) registerCustomFont(selected);
                    runEditorScript("FZWriter.setFont("
                            + JSONObject.quote(selected.cssFamily) + ",true)");
                    statusView.setText("Font: " + selected.name + "  ·  Offline");
                })
                .setNeutralButton("Font Center", (dialog, which) ->
                        startActivity(new Intent(this, com.fz.office.fonts.FontCenterActivity.class)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHindiCharacters() {
        String[] characters = new String[] {
                "ँ", "ं", "ः", "़", "्", "ॐ", "।", "॥", "₹", "ऽ",
                "्र", "क्ष", "त्र", "ज्ञ", "श्र", "क़", "ख़", "ज़", "ड़", "फ़",
                "\u200D ZWJ", "\u200C ZWNJ"
        };
        new AlertDialog.Builder(this)
                .setTitle("Hindi characters and signs")
                .setItems(characters, (dialog, which) -> {
                    String value = characters[which];
                    if (value.contains("ZWJ")) value = "\u200D";
                    else if (value.contains("ZWNJ")) value = "\u200C";
                    runEditorScript("FZWriter.insertText(" + JSONObject.quote(value) + ")");
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void applyPendingHtml() {
        if (!pageReady || editor == null) return;
        try {
            editor.evaluateJavascript("FZWriter.setHtml("
                    + JSONObject.quote(pendingHtml == null ? "<p></p>" : pendingHtml) + ");", null);
        } catch (Exception ignored) {
            editor.evaluateJavascript("FZWriter.setHtml('<p></p>');", null);
        }
    }

    private void updateTitle(String title) {
        documentName = title;
        if (titleView != null) titleView.setText(title);
    }

    private void setDirty(boolean value) {
        dirty = value;
        if (statusView != null) {
            String base = plainText ? "TXT document" : "DOCX document";
            statusView.setText(base + (dirty ? "  ·  Unsaved changes" : "  ·  Saved")
                    + "  ·  Offline");
        }
        if (saveButton != null) {
            saveButton.setEnabled(!saving);
            saveButton.setAlpha(saving ? 0.55f : 1f);
        }
    }

    private void requestSave() {
        if (saving) return;
        if (editor == null || !pageReady) {
            Toast.makeText(this, "Editor is still loading.", Toast.LENGTH_SHORT).show();
            return;
        }
        runEditorScript("FZWriter.requestSave()");
    }

    private void saveHtml(String html) {
        if (saving) return;
        saving = true;
        if (saveButton != null) {
            saveButton.setEnabled(false);
            saveButton.setText("Saving…");
        }
        final String content = html == null ? "<p></p>" : html;
        ioExecutor.execute(() -> {
            try {
                byte[] bytes;
                if (plainText) {
                    bytes = DocxCodec.htmlToPlainText(content).getBytes(StandardCharsets.UTF_8);
                } else {
                    bytes = DocxCodec.writeDocx(content, packageEntries);
                }

                if (sourceUri == null) {
                    pendingSaveBytes = bytes;
                    runOnUiThread(this::launchSaveAs);
                } else {
                    writeToExistingOrFallback(bytes);
                }
            } catch (Exception exception) {
                runOnUiThread(() -> finishSave(false,
                        exception.getMessage() == null ? "Could not prepare the document." : exception.getMessage()));
            }
        });
    }

    private void writeToExistingOrFallback(byte[] bytes) {
        Exception lastError = null;
        String[] modes = new String[] {"rwt", "wt"};
        for (String mode : modes) {
            try (OutputStream output = getContentResolver().openOutputStream(sourceUri, mode)) {
                if (output == null) throw new IOException("The selected storage location is read-only.");
                output.write(bytes);
                output.flush();
                runOnUiThread(() -> finishSave(true, "Document saved."));
                return;
            } catch (Exception exception) {
                lastError = exception;
            }
        }
        pendingSaveBytes = bytes;
        String message = lastError == null ? "Could not overwrite the source file."
                : lastError.getMessage();
        runOnUiThread(() -> {
            Toast.makeText(this, "Original file is read-only. Choose Save As.", Toast.LENGTH_LONG).show();
            launchSaveAs();
        });
    }

    private void launchSaveAs() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(plainText ? "text/plain"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        String suggested = documentName;
        if (suggested == null || suggested.trim().isEmpty()) {
            suggested = plainText ? "Untitled.txt" : "Untitled.docx";
        }
        if (plainText && !suggested.toLowerCase().endsWith(".txt")) suggested += ".txt";
        if (!plainText && !suggested.toLowerCase().endsWith(".docx")) suggested += ".docx";
        intent.putExtra(Intent.EXTRA_TITLE, suggested);
        try {
            startActivityForResult(intent, REQUEST_SAVE_AS);
        } catch (Exception exception) {
            finishSave(false, "No document provider is available for saving.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_AS) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                finishSave(false, "Save As was cancelled.");
                return;
            }
            Uri destination = data.getData();
            byte[] bytes = pendingSaveBytes;
            if (bytes == null) {
                finishSave(false, "No document data is ready to save.");
                return;
            }
            ioExecutor.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new IOException("The destination is not writable.");
                    output.write(bytes);
                    output.flush();
                    sourceUri = destination;
                    documentName = queryDisplayName(destination);
                    plainText = documentName.toLowerCase().endsWith(".txt");
                    pendingSaveBytes = null;
                    runOnUiThread(() -> {
                        updateTitle(documentName);
                        finishSave(true, "Saved as " + documentName);
                    });
                } catch (Exception exception) {
                    runOnUiThread(() -> finishSave(false,
                            exception.getMessage() == null ? "Could not save the document." : exception.getMessage()));
                }
            });
        }
    }

    private void finishSave(boolean success, String message) {
        saving = false;
        if (saveButton != null) {
            saveButton.setEnabled(true);
            saveButton.setText("Save");
        }
        setDirty(!success);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
            name = last == null ? "Untitled.docx" : last;
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

    private void handleBack() {
        if (!dirty) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("Save your changes before closing Writer?")
                .setPositiveButton("Save", (dialog, which) -> requestSave())
                .setNegativeButton("Discard", (dialog, which) -> finish())
                .setNeutralButton("Keep editing", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        if (editor != null) {
            editor.removeJavascriptInterface("AndroidBridge");
            editor.destroy();
            editor = null;
        }
        ioExecutor.shutdown();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class EditorBridge {
        @JavascriptInterface
        public void onEditorReady() {
            runOnUiThread(() -> {
                pageReady = true;
                initializeEditor();
            });
        }

        @JavascriptInterface
        public void onDirty() {
            runOnUiThread(() -> {
                if (!saving) setDirty(true);
            });
        }

        @JavascriptInterface
        public void onSaveHtml(String html) {
            runOnUiThread(() -> saveHtml(html));
        }
    }
}
