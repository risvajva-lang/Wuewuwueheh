package com.cineview.vip;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import com.cineview.app.NativeBridge;
import com.cineview.app.download.DownloadManagerCompat;
import com.cineview.app.update.JsUpdateManager;

import java.util.Locale;

/**
 * Native Android host for the existing CineView web UI.
 * The visual layer remains untouched; this activity only provides the Android shell.
 */
public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4101;
    private static final String APP_HOST = "cineview.xo.je";
    private static final String LOCAL_START = "https://appassets.androidplatform.net/assets/web/index.html";

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ValueCallback<Uri[]> fileCallback;
    private WebViewAssetLoader assetLoader;
    private CineChromeClient chromeClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildNativeShell();
        configureWebView();
        requestNotificationPermissionIfNeeded();
        if (savedInstanceState == null) {
            handleIntent(getIntent());
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildNativeShell() {
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setVisibility(View.GONE);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " CineViewAndroid/" + BuildConfig.VERSION_NAME);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.addJavascriptInterface(new NativeBridge(this, "https://" + APP_HOST + "/", webView), "CineViewNative");
        chromeClient = new CineChromeClient();
        webView.setWebChromeClient(chromeClient);
        webView.setDownloadListener(new CineDownloadListener());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    java.io.File active = JsUpdateManager.activeFile(MainActivity.this);
                    if (active != null && ("/assets/web/assets/index.js".equals(request.getUrl().getPath()) || "/assets/index.js".equals(request.getUrl().getPath()))) {
                        return new WebResourceResponse("application/javascript", "UTF-8", active.toURI().toURL().openStream());
                    }
                } catch (Exception ignored) {
                    // Fall through to the packaged asset.
                }
                WebResourceResponse local = assetLoader.shouldInterceptRequest(request.getUrl());
                return local != null ? local : super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }
        });
    }

    private boolean routeUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);

        if ("cineview".equals(scheme)) {
            dispatchDeepLink(uri.toString());
            return true;
        }
        if ("https".equals(scheme) && (APP_HOST.equals(host) || host.endsWith("." + APP_HOST))) {
            return false;
        }
        if ("https".equals(scheme) && "appassets.androidplatform.net".equals(host)) {
            String path = uri.getPath() == null ? "/" : uri.getPath();
            if (!path.startsWith("/assets/")) {
                StringBuilder value = new StringBuilder(path);
                if (uri.getQuery() != null) value.append("?").append(uri.getQuery());
                if (uri.getFragment() != null) value.append("#").append(uri.getFragment());
                String target = org.json.JSONObject.quote(value.toString());
                webView.evaluateJavascript("history.pushState({},''," + target + ");window.dispatchEvent(new PopStateEvent('popstate'));", null);
                return true;
            }
        }
        if ("https".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
                // No compatible external browser/player.
            }
        }
        return true;
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent != null && intent.getData() != null && "cineview".equals(intent.getData().getScheme())) {
            dispatchDeepLink(intent.getData().toString());
        } else {
            webView.loadUrl(LOCAL_START);
        }
    }

    private void dispatchDeepLink(String value) {
        if (webView == null) return;
        String safe = org.json.JSONObject.quote(value);
        webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('cineview://deep-link',{detail:" + safe + "}));",
                null
        );
    }

    private final class CineChromeClient extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                onHideCustomView();
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(-1, -1));
            fullscreenContainer.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
            enterFullscreen();
        }

        @Override
        public void onHideCustomView() {
            if (fullscreenView == null) return;
            fullscreenContainer.removeView(fullscreenView);
            fullscreenContainer.setVisibility(View.GONE);
            fullscreenView = null;
            webView.setVisibility(View.VISIBLE);
            exitFullscreen();
            if (fullscreenCallback != null) {
                fullscreenCallback.onCustomViewHidden();
                fullscreenCallback = null;
            }
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            Intent intent;
            try {
                intent = params.createIntent();
            } catch (Exception e) {
                intent = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            }
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (Exception e) {
                fileCallback = null;
                return false;
            }
        }
    }

    private final class CineDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
            if (url == null || !url.startsWith("https://")) return;
            String title = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManagerCompat.enqueue(MainActivity.this, url, title);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9201);
        }
    }

    private void enterFullscreen() {
        Window window = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void exitFullscreen() {
        Window window = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.show(WindowInsets.Type.systemBars());
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            chromeClient.onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                result = new Uri[count];
                for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
