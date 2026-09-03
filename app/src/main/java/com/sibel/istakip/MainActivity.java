package com.sibel.istakip;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://sibel-is-takip.sblkiziloz.chatgpt.site";
    private static final int FILE_CHOOSER_REQUEST = 4127;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> pendingFileChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createLayout();
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void createLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(246, 247, 251));

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.gravity = android.view.Gravity.TOP;
        root.addView(progressBar, progressParams);

        setContentView(root);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new DownloadBridge(), "AndroidBridge");
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());
        webView.setDownloadListener(new AppDownloadListener());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST && pendingFileChooser != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            pendingFileChooser.onReceiveValue(result);
            pendingFileChooser = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showOfflinePage() {
        String html = "<!doctype html><html lang='tr'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#f6f7fb;color:#18203a;display:grid;place-items:center;height:100vh;margin:0}"
                + ".c{text-align:center;padding:32px}.i{font-size:52px}button{border:0;border-radius:12px;background:#4f46e5;color:#fff;padding:13px 22px;font-weight:700}</style>"
                + "<div class='c'><div class='i'>📡</div><h2>Bağlantı yok</h2><p>İş Takip'e ulaşmak için internet bağlantını kontrol et.</p>"
                + "<button onclick=\"location.href='" + HOME_URL + "'\">Tekrar dene</button></div></html>";
        webView.loadDataWithBaseURL(HOME_URL, html, "text/html", "UTF-8", null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeFilename(String requested, String mimeType) {
        String name = requested == null ? "is-takip-raporu" : requested;
        name = name.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (name.isEmpty()) name = "is-takip-raporu";
        if (!name.contains(".")) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null && !extension.isEmpty()) name += "." + extension;
        }
        return name;
    }

    private void saveBytes(String filename, String mimeType, byte[] bytes) throws Exception {
        String cleanName = safeFilename(filename, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IsTakip");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Dosya konumu oluşturulamadı");
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null) throw new IllegalStateException("Dosya açılamadı");
                stream.write(bytes);
            }
        } else {
            File folder = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "IsTakip");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Klasör oluşturulamadı");
            try (OutputStream stream = new FileOutputStream(new File(folder, cleanName))) {
                stream.write(bytes);
            }
        }
        runOnUiThread(() -> Toast.makeText(this, "Dosya İndirilenler/IsTakip klasörüne kaydedildi", Toast.LENGTH_LONG).show());
    }

    private final class AppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                view.loadUrl(uri.toString());
                return true;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(MainActivity.this, "Bu bağlantıyı açacak uygulama bulunamadı", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame() && !isOnline()) showOfflinePage();
        }
    }

    private final class AppWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = callback;
            Intent intent;
            try {
                intent = params.createIntent();
            } catch (Exception error) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException error) {
                pendingFileChooser = null;
                Toast.makeText(MainActivity.this, "Dosya seçici bulunamadı", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private final class AppDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
            String filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (url.startsWith("blob:")) {
                String script = "(async()=>{try{const r=await fetch(" + JSONObject.quote(url) + ");"
                        + "const b=await r.blob();const fr=new FileReader();fr.onloadend=()=>AndroidBridge.saveBase64("
                        + JSONObject.quote(filename) + ",b.type||" + JSONObject.quote(mimeType) + ",fr.result.split(',')[1]);"
                        + "fr.readAsDataURL(b)}catch(e){AndroidBridge.downloadFailed()}})()";
                webView.evaluateJavascript(script, null);
                return;
            }

            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) request.addRequestHeader("Cookie", cookie);
                request.setTitle(filename);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "IsTakip/" + safeFilename(filename, mimeType));
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(request);
                Toast.makeText(MainActivity.this, "İndirme başlatıldı", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(MainActivity.this, "Dosya indirilemedi", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class DownloadBridge {
        @JavascriptInterface
        public void saveBase64(String filename, String mimeType, String payload) {
            try {
                byte[] bytes = Base64.decode(payload, Base64.DEFAULT);
                saveBytes(filename, mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType, bytes);
            } catch (Exception error) {
                downloadFailed();
            }
        }

        @JavascriptInterface
        public void downloadFailed() {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Dosya kaydedilemedi", Toast.LENGTH_SHORT).show());
        }
    }
}
