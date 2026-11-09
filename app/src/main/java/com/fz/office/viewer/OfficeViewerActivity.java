package com.fz.office.viewer;

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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fz.office.fonts.FontManager;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OfficeViewerActivity extends Activity {
    public static final String EXTRA_DOCUMENT_URI = "document_uri";
    public static final String EXTRA_DOCUMENT_NAME = "document_name";
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String CSP =
            "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
            + "font-src 'self' data: blob:; media-src 'self' data: blob:; "
            + "connect-src 'self'; worker-src 'self'; frame-src 'none'; "
            + "object-src 'none'; form-action 'none'; base-uri 'none'";

    private Uri documentUri;
    private String documentName = "Office document";
    private String extension = "";
    private String pageName = "xlsx.html";
    private String documentPath = "";
    private boolean presentation;
    private boolean editableXlsx;
    private WebView webView;
    private ProgressBar progress;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String value = getIntent().getStringExtra(EXTRA_DOCUMENT_URI);
        if (value != null && !value.isEmpty()) documentUri = Uri.parse(value);
        String suppliedName = getIntent().getStringExtra(EXTRA_DOCUMENT_NAME);
        documentName = suppliedName == null || suppliedName.trim().isEmpty()
                ? (documentUri == null ? "Office document" : queryDisplayName(documentUri))
                : suppliedName;
        extension = extensionOf(documentName);
        String mime = documentUri == null ? null : getContentResolver().getType(documentUri);
        if (extension.isEmpty()) {
            String type = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
            if (type.contains("presentation")) extension = "pptx";
            else if (type.contains("excel") || type.contains("spreadsheet")) extension = "xlsx";
        }
        presentation = isPresentationExtension(extension) || isPresentationMime(mime);
        // Resolve the final extension first: providers sometimes omit the filename suffix
        // while still supplying the XLSX MIME type.
        editableXlsx = !presentation && "xlsx".equals(extension);
        if (documentUri == null || (!presentation && !isSpreadsheetExtension(extension) && !isSpreadsheetMime(mime))) {
            Toast.makeText(this, "This file type is not supported by the reader.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (presentation) {
            pageName = "pptx.html";
            documentPath = "/doc/file.pptx";
        } else {
            pageName = "xlsx.html";
            if (extension.isEmpty()) extension = "xlsx";
            documentPath = "/doc/file." + extension;
        }
        buildViewer();
    }

    private void buildViewer() {
        getWindow().setStatusBarColor(0xff151d1e);
        getWindow().setNavigationBarColor(0xff151d1e);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff3f1ec);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(7), dp(6), dp(8), dp(6));
        toolbar.setBackgroundColor(Color.WHITE);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView back = control("‹", 28, 0xff263c38);
        back.setGravity(Gravity.CENTER);
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(44)));
        back.setOnClickListener(v -> finish());

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);
        titleColumn.setPadding(dp(5), 0, dp(3), 0);
        toolbar.addView(titleColumn, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView title = control(documentName, 13, 0xff172c28);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titleColumn.addView(title);
        TextView format = control(presentation ? "POWERPOINT · VIEW ONLY"
                : (editableXlsx ? "SPREADSHEET · EDITABLE COPY" : "SPREADSHEET · VIEW ONLY"),
                9, 0xff60776e);
        format.setLetterSpacing(0.07f);
        titleColumn.addView(format);

        if (editableXlsx && !presentation) {
            TextView edit = toolbarControl("Edit");
            toolbar.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(40)));
            edit.setOnClickListener(v -> {
                Intent intent = new Intent(OfficeViewerActivity.this, SpreadsheetEditorActivity.class);
                intent.putExtra(SpreadsheetEditorActivity.EXTRA_DOCUMENT_URI, documentUri.toString());
                intent.putExtra(SpreadsheetEditorActivity.EXTRA_DOCUMENT_NAME, documentName);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            });
        }

        TextView find = toolbarControl("Find");
        toolbar.addView(find, new LinearLayout.LayoutParams(dp(48), dp(40)));
        find.setOnClickListener(v -> showSearch());

        TextView zoomOut = toolbarControl("−");
        toolbar.addView(zoomOut, new LinearLayout.LayoutParams(dp(34), dp(40)));
        zoomOut.setOnClickListener(v -> { if (webView != null) webView.zoomOut(); });

        TextView zoomIn = toolbarControl("+");
        toolbar.addView(zoomIn, new LinearLayout.LayoutParams(dp(34), dp(40)));
        zoomIn.setOnClickListener(v -> { if (webView != null) webView.zoomIn(); });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        webView = new WebView(this);
        webView.setBackgroundColor(0xffece8df);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMinimumFontSize(presentation ? 8 : 1);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            if (numberOfMatches > 0 && status != null) {
                status.setText("Found " + numberOfMatches + " match(es)");
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return intercept(request.getUrl());
            }
            @Override @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return intercept(Uri.parse(url));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isTrustedViewerPage(request.getUrl());
            }
            @Override @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isTrustedViewerPage(Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (status != null) status.setText("Opening document…");
                if (progress != null) progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (progress != null) progress.setVisibility(View.GONE);
                if (status != null) status.setText("Read only · Original file is never changed");
                String family = FontManager.getDefaultFont(OfficeViewerActivity.this);
                String script = "document.documentElement.style.setProperty('--fz-hindi-default',"
                        + JSONObject.quote(family) + ");window.dispatchEvent(new Event('load'));";
                view.evaluateJavascript(script, null);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    if (progress != null) progress.setVisibility(View.GONE);
                    if (status != null) status.setText("Could not load this document");
                    Toast.makeText(OfficeViewerActivity.this, "The viewer page could not be loaded.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        status = control("Preparing offline viewer…", 10, 0xff60766e);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(13), dp(8), dp(13), dp(8));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        applySafeAreaInsets(root);

        String viewerUrl = "https://" + ASSET_HOST + "/assets/viewer/" + pageName
                + "?name=" + Uri.encode(documentName)
                + "&ext=" + Uri.encode(extension)
                + "&ranged=0";
        webView.loadUrl(viewerUrl);
    }

    private WebResourceResponse intercept(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || !ASSET_HOST.equals(uri.getHost())) {
            return notFound();
        }
        String path = uri.getPath();
        if (path == null || path.contains("..")) return notFound();
        if (documentPath.equals(path)) return documentResponse();
        if (!path.startsWith("/assets/")) return notFound();

        String relative = path.substring("/assets/".length());
        if (relative.equals("viewer/hindi-fonts.css")) return hindiFontCss();
        if (relative.startsWith("viewer/fonts/")) {
            relative = "writer/fonts/" + relative.substring("viewer/fonts/".length());
        }
        try {
            InputStream input = getAssets().open(relative);
            return response(assetMime(relative), isTextAsset(relative) ? "utf-8" : null,
                    200, "OK", input, true);
        } catch (IOException e) {
            return notFound();
        }
    }

    private WebResourceResponse documentResponse() {
        try {
            InputStream input = getContentResolver().openInputStream(documentUri);
            if (input == null) return notFound();
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("Cache-Control", "no-store");
            long length = -1;
            try (android.content.res.AssetFileDescriptor descriptor =
                         getContentResolver().openAssetFileDescriptor(documentUri, "r")) {
                if (descriptor != null) length = descriptor.getLength();
            } catch (Exception ignored) { }
            if (length > 0) headers.put("Content-Length", String.valueOf(length));
            return new WebResourceResponse(documentMime(), null, 200, "OK", headers, input);
        } catch (Exception e) {
            return notFound();
        }
    }

    private WebResourceResponse hindiFontCss() {
        try {
            String family = FontManager.getDefaultFont(this);
            StringBuilder css = new StringBuilder(FontManager.getBundledCss());
            FontManager.FontRecord selected = FontManager.findByFamily(this, family);
            if (selected != null && !selected.bundled) css.append(FontManager.getCustomFontCss(selected));
            css.append(":root{--fz-hindi-default:").append(cssString(family)).append(";}");
            return response("text/css", "utf-8", 200, "OK",
                    new java.io.ByteArrayInputStream(css.toString().getBytes("UTF-8")), true);
        } catch (Exception e) {
            return response("text/css", "utf-8", 200, "OK",
                    new java.io.ByteArrayInputStream(new byte[0]), true);
        }
    }

    private WebResourceResponse response(String mime, String encoding, int code, String reason,
                                         InputStream input, boolean withPolicy) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Cache-Control", "no-store");
        if (withPolicy) headers.put("Content-Security-Policy", CSP);
        return new WebResourceResponse(mime, encoding, code, reason, headers, input);
    }

    private WebResourceResponse notFound() {
        return response("text/plain", "utf-8", 404, "Not Found",
                new java.io.ByteArrayInputStream(new byte[0]), true);
    }

    private boolean isTrustedViewerPage(Uri uri) {
        return uri != null && "https".equals(uri.getScheme()) && ASSET_HOST.equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/assets/viewer/")
                && uri.getPath().endsWith(".html") && !uri.getPath().contains("..");
    }

    private String documentMime() {
        if (presentation) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if ("csv".equals(extension)) return "text/csv";
        if ("ods".equals(extension)) return "application/vnd.oasis.opendocument.spreadsheet";
        if ("xls".equals(extension) || "xlsm".equals(extension) || "xlsb".equals(extension)) {
            return "application/vnd.ms-excel";
        }
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
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

    private String cssString(String value) {
        String safe = value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "").replace("\r", "");
        return "\"" + safe + "\"";
    }

    private void showSearch() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Find text in this file");
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        content.addView(query, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Find in document")
                .setView(content)
                .setNegativeButton("Close", (d, w) -> { if (webView != null) webView.clearMatches(); })
                .setNeutralButton("Previous", null)
                .setPositiveButton("Next", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                    findText(query.getText().toString(), true));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    findText(query.getText().toString(), false));
        });
        dialog.show();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        query.requestFocus();
    }

    private void findText(String query, boolean forward) {
        if (webView == null || query == null || query.trim().isEmpty()) return;
        webView.findAllAsync(query.trim());
        webView.findNext(forward);
    }

    private boolean isPresentationExtension(String ext) {
        return "pptx".equals(ext) || "ppsx".equals(ext) || "pptm".equals(ext) || "potx".equals(ext);
    }
    private boolean isSpreadsheetExtension(String ext) {
        return "xlsx".equals(ext) || "xls".equals(ext) || "xlsm".equals(ext) || "xlsb".equals(ext)
                || "xltx".equals(ext) || "csv".equals(ext) || "ods".equals(ext);
    }
    private boolean isPresentationMime(String mime) {
        if (mime == null) return false;
        String type = mime.toLowerCase(Locale.ROOT);
        return type.contains("presentation") || type.contains("powerpoint");
    }
    private boolean isSpreadsheetMime(String mime) {
        if (mime == null) return false;
        String type = mime.toLowerCase(Locale.ROOT);
        return type.contains("spreadsheet") || type.contains("excel") || "text/csv".equals(type);
    }
    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "Office document" : uri.getLastPathSegment();
    }
    private TextView toolbarControl(String value) {
        TextView view = control(value, 12, 0xff31534b);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(0xffeef3f0, 12));
        return view;
    }
    private TextView control(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() { finish(); }
    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
