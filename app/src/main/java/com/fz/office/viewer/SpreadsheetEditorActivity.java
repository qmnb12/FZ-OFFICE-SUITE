package com.fz.office.viewer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SpreadsheetEditorActivity extends Activity {
    public static final String EXTRA_DOCUMENT_URI = "document_uri";
    public static final String EXTRA_DOCUMENT_NAME = "document_name";
    private static final int REQUEST_CREATE_COPY = 4102;
    private static final int MAX_BASE64_CHARS = 42 * 1024 * 1024;
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String DOCUMENT_PATH = "/doc/file.xlsx";
    private static final String CSP =
            "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
            + "font-src 'self' data: blob:; connect-src 'self'; worker-src 'self'; "
            + "object-src 'none'; form-action 'none'; base-uri 'none'";

    private Uri source;
    private String documentName = "Workbook.xlsx";
    private byte[] pendingBytes;
    private WebView webView;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String uri = getIntent().getStringExtra(EXTRA_DOCUMENT_URI);
        if (uri != null && !uri.isEmpty()) source = Uri.parse(uri);
        String supplied = getIntent().getStringExtra(EXTRA_DOCUMENT_NAME);
        if (supplied != null && !supplied.trim().isEmpty()) documentName = supplied;
        if (source == null || !documentName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            Toast.makeText(this, "Editing is currently supported for .xlsx workbooks.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(0xfff4f7f5);
        getWindow().setNavigationBarColor(0xfff4f7f5);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffeef3f0);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(9), dp(5), dp(12), dp(5));
        toolbar.setBackgroundColor(Color.WHITE);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView back = label("‹", 26, 0xff1d5145, true);
        back.setGravity(Gravity.CENTER);
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(38), -1));
        back.setOnClickListener(v -> finish());

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);
        titleColumn.setPadding(dp(5), 0, 0, 0);
        toolbar.addView(titleColumn, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView title = label(documentName, 13, 0xff18352d, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titleColumn.addView(title);
        TextView subtitle = label("FZ SHEETS · EDIT A COPY", 9, 0xff60766d, true);
        subtitle.setLetterSpacing(0.06f);
        titleColumn.addView(subtitle);

        webView = new WebView(this);
        webView.setBackgroundColor(0xffeef3f0);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadsImagesAutomatically(false);
        settings.setBlockNetworkImage(true);
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");
        webView.addJavascriptInterface(new EditorBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return intercept(request.getUrl());
            }
            @Override @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return intercept(Uri.parse(url));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isEditorPage(request.getUrl());
            }
            @Override @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isEditorPage(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                setStatus("Opening spreadsheet…");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) setStatus("Could not load the spreadsheet editor");
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        status = label("Preparing offline spreadsheet editor…", 10, 0xff60766d, false);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();

        String page = "https://" + ASSET_HOST + "/assets/writer/sheet-editor.html?name="
                + Uri.encode(documentName);
        webView.loadUrl(page);
    }

    private WebResourceResponse intercept(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || !ASSET_HOST.equals(uri.getHost())) {
            return notFound();
        }
        String path = uri.getPath();
        if (path == null || path.contains("..")) return notFound();
        if (DOCUMENT_PATH.equals(path)) return documentResponse();
        if (!path.startsWith("/assets/")) return notFound();
        String relative = path.substring("/assets/".length());
        try {
            InputStream input = getAssets().open(relative);
            return response(assetMime(relative), isTextAsset(relative) ? "utf-8" : null,
                    200, "OK", input);
        } catch (IOException e) {
            return notFound();
        }
    }

    private WebResourceResponse documentResponse() {
        try {
            InputStream input = getContentResolver().openInputStream(source);
            if (input == null) return notFound();
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    null, 200, "OK", headers, input);
        } catch (Exception e) {
            return notFound();
        }
    }

    private WebResourceResponse response(String mime, String encoding, int code,
                                         String reason, InputStream input) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Cache-Control", "no-store");
        headers.put("Content-Security-Policy", CSP);
        return new WebResourceResponse(mime, encoding, code, reason, headers, input);
    }

    private WebResourceResponse notFound() {
        return response("text/plain", "utf-8", 404, "Not Found",
                new ByteArrayInputStream(new byte[0]));
    }

    private boolean isEditorPage(Uri uri) {
        return uri != null && "https".equals(uri.getScheme()) && ASSET_HOST.equals(uri.getHost())
                && "/assets/writer/sheet-editor.html".equals(uri.getPath());
    }

    private String assetMime(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private boolean isTextAsset(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".js")
                || lower.endsWith(".json") || lower.endsWith(".txt");
    }

    private void setStatus(String message) {
        runOnUiThread(() -> {
            if (status != null) status.setText(message);
        });
    }

    private void saveWorkbook(String base64) {
        if (base64 == null || base64.isEmpty() || base64.length() > MAX_BASE64_CHARS) {
            setStatus("Workbook is too large to save from this editor");
            return;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                setStatus("The edited workbook package is invalid");
                return;
            }
            pendingBytes = bytes;
            runOnUiThread(this::pickSaveLocation);
        } catch (Exception e) {
            setStatus("Could not prepare the edited workbook");
        }
    }

    private void pickSaveLocation() {
        if (pendingBytes == null) return;
        String base = documentName.substring(0, documentName.length() - 5);
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        create.putExtra(Intent.EXTRA_TITLE, base + " - edited.xlsx");
        try {
            startActivityForResult(create, REQUEST_CREATE_COPY);
        } catch (Exception e) {
            setStatus("Could not open the Save As picker");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREATE_COPY) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || pendingBytes == null) {
            pendingBytes = null;
            setStatus("Save cancelled · Original unchanged");
            return;
        }
        Uri target = data.getData();
        byte[] bytes = pendingBytes;
        pendingBytes = null;
        setStatus("Saving copy…");
        try (OutputStream out = getContentResolver().openOutputStream(target, "w")) {
            if (out == null) throw new IOException("Could not open the selected save location.");
            out.write(bytes);
            out.flush();
            setStatus("Edited workbook copy saved · Original unchanged");
            Toast.makeText(this, "Edited XLSX copy saved", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            setStatus("Could not save the edited workbook copy");
            Toast.makeText(this, e.getMessage() == null ? "Save failed." : e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private final class EditorBridge {
        @JavascriptInterface public void saveWorkbook(String base64) {
            SpreadsheetEditorActivity.this.saveWorkbook(base64);
        }
        @JavascriptInterface public void setStatus(String message) {
            SpreadsheetEditorActivity.this.setStatus(message == null ? "" : message);
        }
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
