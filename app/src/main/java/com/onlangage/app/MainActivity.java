package com.onlangage.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.*;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.view.View;
import android.widget.FrameLayout;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import java.io.*;

public class MainActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private static final int SAVE_PDF = 301;
    private File pendingPdf;
    private TextToSpeech tts;
    private boolean voiceReady = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        tts = new TextToSpeech(this, status -> {
            voiceReady = status == TextToSpeech.SUCCESS;
            if (!voiceReady) message("Motè vwa Android pa disponib");
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void saveCertificate(String html) {
                runOnUiThread(() -> generatePdf(html, false));
            }
            @JavascriptInterface public void speak(String text, String language) {
                runOnUiThread(() -> speakText(text, language));
            }
            @JavascriptInterface public void shareCertificate(String html) {
                runOnUiThread(() -> generatePdf(html, true));
            }
        }, "ONLangageAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) return false;
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    private void speakText(String text, String language) {
        if (!voiceReady || tts == null) {
            message("Aktive motè Text-to-Speech sou telefòn nan");
            return;
        }
        Locale locale = Locale.forLanguageTag(language == null ? "en-US" : language);
        int available = tts.setLanguage(locale);
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            message("Vwa pou lang sa a pa enstale sou telefòn nan");
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "onlangage-voice");
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void generatePdf(String html, boolean share) {
        if (html == null || html.trim().isEmpty()) {
            message("Certificat indisponible");
            return;
        }
        WebView printer = new WebView(this);
        // An unattached WebView may never finish loading on Android.
        FrameLayout.LayoutParams pdfParams = new FrameLayout.LayoutParams(1122, 794);
        printer.setAlpha(0.01f);
        root.addView(printer, pdfParams);
        printer.setBackgroundColor(Color.WHITE);
        printer.getSettings().setDefaultTextEncodingName("UTF-8");
        printer.getSettings().setJavaScriptEnabled(false);
        printer.setWebViewClient(new WebViewClient() {
            boolean started = false;
            @Override public void onPageFinished(WebView view, String url) {
                if (started) return;
                started = true;
                printer.postDelayed(() -> writePdf(printer, share), 800);
            }
        });
        printer.loadDataWithBaseURL("https://on-langage.local/", html, "text/html", "UTF-8", null);
    }

    private void writePdf(WebView printer, boolean share) {
        PdfDocument document = new PdfDocument();
        try {
            final int pageWidth = 842;
            final int pageHeight = 595;
            final int renderWidth = 794;
            printer.measure(View.MeasureSpec.makeMeasureSpec(renderWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int contentHeight = Math.max(794, printer.getMeasuredHeight());
            printer.layout(0, 0, renderWidth, contentHeight);
            float scale = (float) pageWidth / renderWidth;
            float sourcePageHeight = pageHeight / scale;
            int pages = Math.max(1, (int) Math.ceil(contentHeight / sourcePageHeight));
            for (int index = 0; index < pages; index++) {
                PdfDocument.Page page = document.startPage(
                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create());
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                canvas.save();
                canvas.scale(scale, scale);
                canvas.translate(0, -index * sourcePageHeight);
                printer.draw(canvas);
                canvas.restore();
                document.finishPage(page);
            }
            File folder = new File(getCacheDir(), "certificates");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Dosye PDF pa disponib");
            File pdf = new File(folder, "ON-LANGAGE-Certificat-" + System.currentTimeMillis() + ".pdf");
            try (OutputStream out = new FileOutputStream(pdf)) {
                document.writeTo(out);
            }
            if (pdf.length() == 0) throw new IOException("PDF vid");
            pendingPdf = pdf;
            if (share) sharePdf(pdf); else chooseSave();
        } catch (Exception e) {
            message("Erè PDF: " + e.getMessage());
        } finally {
            document.close();
            root.removeView(printer);
            printer.destroy();
        }
    }

    private void chooseSave() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_TITLE, "ON-LANGAGE-Certificat.pdf");
            startActivityForResult(intent, SAVE_PDF);
        } catch (Exception e) {
            message("Pa ka ouvri anrejistreman: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SAVE_PDF && result == RESULT_OK && data != null && pendingPdf != null) {
            try (InputStream in = new FileInputStream(pendingPdf);
                 OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IOException("Destination indisponible");
                copy(in, out);
                message("Certificat PDF anrejistre");
            } catch (Exception e) {
                message("Pa ka anrejistre PDF: " + e.getMessage());
            }
        }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }

    private void sharePdf(File pdf) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            message("Sou Android sa a, anrejistre PDF la avan ou pataje li.");
            chooseSave();
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, pdf.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ON-LANGAGE");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Pa ka kreye PDF pataj la");
            try (InputStream in = new FileInputStream(pdf);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Pa ka ekri PDF la");
                copy(in, out);
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Pataje certificat PDF"));
        } catch (Exception e) {
            message("Pa ka pataje PDF: " + e.getMessage());
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
