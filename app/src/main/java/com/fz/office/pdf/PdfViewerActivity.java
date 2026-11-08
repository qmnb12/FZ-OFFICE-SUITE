package com.fz.office.pdf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline, read-only PDF reader. A bounded private cache copy makes provider streams seekable. */
public final class PdfViewerActivity extends Activity {
    public static final String EXTRA_DOCUMENT_URI = "document_uri";
    public static final String EXTRA_DOCUMENT_NAME = "document_name";
    private static final int MAX_PAGE_DIMENSION = 4096;
    private static final long MAX_BITMAP_PIXELS = 8_000_000L;
    private static final long MAX_PDF_BYTES = 512L * 1024L * 1024L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri documentUri;
    private String documentName = "PDF document";
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private File cachedPdf;
    private ImageView pageImage;
    private TextView pageLabel;
    private TextView status;
    private ProgressBar progress;
    private Bitmap displayedBitmap;
    private int pageIndex;
    private int pageCount;
    private int renderGeneration;
    private float zoom = 1.0f;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String rawUri = getIntent().getStringExtra(EXTRA_DOCUMENT_URI);
        if (rawUri == null || rawUri.trim().isEmpty()) {
            Toast.makeText(this, "No PDF file was selected.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        documentUri = Uri.parse(rawUri);
        String suppliedName = getIntent().getStringExtra(EXTRA_DOCUMENT_NAME);
        if (suppliedName != null && !suppliedName.trim().isEmpty()) documentName = suppliedName;
        buildScreen();
        openPdf();
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(0xff151d1e);
        getWindow().setNavigationBarColor(0xff151d1e);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffe9eeeb);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(5), dp(8), dp(5));
        toolbar.setBackgroundColor(Color.WHITE);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView back = control("‹", 28, 0xff263c38);
        back.setGravity(Gravity.CENTER);
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44)));
        back.setOnClickListener(v -> finish());

        TextView title = control(documentName, 13, 0xff172c28);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        title.setPadding(dp(8), 0, dp(5), 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView zoomOut = toolButton("−");
        toolbar.addView(zoomOut, new LinearLayout.LayoutParams(dp(38), dp(42)));
        zoomOut.setOnClickListener(v -> changeZoom(-0.15f));
        TextView zoomIn = toolButton("+");
        toolbar.addView(zoomIn, new LinearLayout.LayoutParams(dp(38), dp(42)));
        zoomIn.setOnClickListener(v -> changeZoom(0.15f));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        horizontal.setBackgroundColor(0xffe9eeeb);
        horizontal.setClipToPadding(false);
        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(false);
        vertical.setBackgroundColor(0xffe9eeeb);
        pageImage = new ImageView(this);
        pageImage.setBackgroundColor(Color.WHITE);
        pageImage.setScaleType(ImageView.ScaleType.CENTER);
        pageImage.setAdjustViewBounds(false);
        vertical.addView(pageImage, new ScrollView.LayoutParams(-2, -2));
        horizontal.addView(vertical, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(horizontal, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout pageControls = new LinearLayout(this);
        pageControls.setGravity(Gravity.CENTER_VERTICAL);
        pageControls.setPadding(dp(12), dp(6), dp(12), dp(6));
        pageControls.setBackgroundColor(Color.WHITE);
        root.addView(pageControls, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView previous = toolButton("Previous");
        pageControls.addView(previous, new LinearLayout.LayoutParams(0, dp(40), 1f));
        previous.setOnClickListener(v -> changePage(-1));
        pageLabel = control("Page — / —", 12, 0xff31534b);
        pageLabel.setGravity(Gravity.CENTER);
        pageControls.addView(pageLabel, new LinearLayout.LayoutParams(dp(105), dp(40)));
        TextView next = toolButton("Next");
        pageControls.addView(next, new LinearLayout.LayoutParams(0, dp(40), 1f));
        next.setOnClickListener(v -> changePage(1));

        status = control("Preparing offline PDF…", 10, 0xff60766e);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(13), dp(7), dp(13), dp(7));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        applySafeAreaInsets(root);
        updatePageLabel();
    }

    private void openPdf() {
        status.setText("Preparing offline PDF…");
        worker.execute(() -> {
            ParcelFileDescriptor opened = null;
            try {
                cachedPdf = copyToPrivateCache();
                opened = ParcelFileDescriptor.open(cachedPdf, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer loaded = new PdfRenderer(opened);
                descriptor = opened;
                renderer = loaded;
                pageCount = loaded.getPageCount();
                if (pageCount < 1) throw new IllegalStateException("This PDF has no pages.");
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    updatePageLabel();
                    renderCurrentPage();
                });
            } catch (Exception exception) {
                if (opened != null && descriptor != opened) {
                    try { opened.close(); } catch (Exception ignored) { }
                }
                runOnUiThread(() -> showError(exception.getMessage()));
            }
        });
    }

    private File copyToPrivateCache() throws IOException {
        File target = File.createTempFile("fz-office-pdf-", ".pdf", getCacheDir());
        long total = 0;
        try (InputStream input = getContentResolver().openInputStream(documentUri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("The selected PDF cannot be read.");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_PDF_BYTES) throw new IOException("PDF is larger than the 512 MB safety limit.");
                output.write(buffer, 0, count);
            }
            output.flush();
            return target;
        } catch (Exception exception) {
            target.delete();
            if (exception instanceof IOException) throw (IOException) exception;
            throw new IOException("Could not prepare the selected PDF.", exception);
        }
    }

    private void renderCurrentPage() {
        if (renderer == null || pageIndex < 0 || pageIndex >= pageCount) return;
        final int generation = ++renderGeneration;
        final int targetPage = pageIndex;
        final float targetZoom = zoom;
        progress.setVisibility(View.VISIBLE);
        status.setText("Rendering page " + (targetPage + 1) + "…");
        updatePageLabel();
        worker.execute(() -> {
            Bitmap bitmap = null;
            try (PdfRenderer.Page page = renderer.openPage(targetPage)) {
                int screenWidth = Math.max(dp(320),
                        getResources().getDisplayMetrics().widthPixels - dp(28));
                float scale = (screenWidth / (float) page.getWidth()) * targetZoom;
                float maxDimensionScale = Math.min(
                        MAX_PAGE_DIMENSION / (float) page.getWidth(),
                        MAX_PAGE_DIMENSION / (float) page.getHeight());
                scale = Math.min(scale, maxDimensionScale);
                double pixels = (double) page.getWidth() * page.getHeight() * scale * scale;
                if (pixels > MAX_BITMAP_PIXELS) scale *= (float) Math.sqrt(MAX_BITMAP_PIXELS / pixels);
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                final Bitmap ready = bitmap;
                bitmap = null;
                runOnUiThread(() -> {
                    if (generation != renderGeneration || isFinishing() || pageImage == null) {
                        ready.recycle();
                        return;
                    }
                    Bitmap previous = displayedBitmap;
                    displayedBitmap = ready;
                    pageImage.setImageBitmap(ready);
                    pageImage.requestLayout();
                    if (previous != null && !previous.isRecycled()) previous.recycle();
                    progress.setVisibility(View.GONE);
                    status.setText("PDF · Read only · Original file unchanged");
                    updatePageLabel();
                });
            } catch (Exception exception) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                runOnUiThread(() -> {
                    if (generation != renderGeneration || isFinishing()) return;
                    progress.setVisibility(View.GONE);
                    status.setText("Could not render this PDF page");
                    Toast.makeText(this, "PDF page render failed.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void changePage(int direction) {
        if (pageCount < 1) return;
        int next = Math.max(0, Math.min(pageCount - 1, pageIndex + direction));
        if (next == pageIndex) return;
        pageIndex = next;
        renderCurrentPage();
    }

    private void changeZoom(float delta) {
        if (renderer == null) return;
        float next = Math.max(0.7f, Math.min(3.0f, zoom + delta));
        if (Math.abs(next - zoom) < 0.001f) return;
        zoom = next;
        renderCurrentPage();
    }

    private void updatePageLabel() {
        if (pageLabel == null) return;
        pageLabel.setText(pageCount > 0
                ? "Page " + (pageIndex + 1) + " / " + pageCount : "Page — / —");
    }

    private void showError(String message) {
        if (isFinishing()) return;
        progress.setVisibility(View.GONE);
        status.setText("Could not open this PDF");
        new android.app.AlertDialog.Builder(this)
                .setTitle("PDF could not be opened")
                .setMessage(message == null || message.trim().isEmpty()
                        ? "The file may be damaged, encrypted or unavailable." : message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void applySafeAreaInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    private TextView toolButton(String value) {
        TextView view = control(value, 12, 0xff31534b);
        view.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(0xffeef3f0);
        background.setCornerRadius(dp(11));
        view.setBackground(background);
        return view;
    }

    private TextView control(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        renderGeneration++;
        if (pageImage != null) pageImage.setImageDrawable(null);
        if (displayedBitmap != null && !displayedBitmap.isRecycled()) displayedBitmap.recycle();
        worker.execute(() -> {
            if (renderer != null) {
                try { renderer.close(); } catch (Exception ignored) { }
                renderer = null;
            }
            if (descriptor != null) {
                try { descriptor.close(); } catch (Exception ignored) { }
                descriptor = null;
            }
            if (cachedPdf != null) {
                try { cachedPdf.delete(); } catch (Exception ignored) { }
                cachedPdf = null;
            }
        });
        worker.shutdown();
        super.onDestroy();
    }
}
