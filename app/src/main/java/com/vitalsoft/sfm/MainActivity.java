package com.vitalsoft.sfm;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Log;

public class MainActivity extends Activity {

    private static final String TAG = "SFM_Main";
    private static final int REQ_RECORD_AUDIO = 1001;

    private WebView webView;
    private AudioListenerManager audioListenerManager;
    private StorageManager storageManager;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen — no action bar, no status bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        hideSystemUI();

        setContentView(R.layout.activity_main);

        storageManager = new StorageManager(this);
        audioListenerManager = new AudioListenerManager(this);

        webView = (WebView) findViewById(R.id.webview);
        setupWebView();

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioListenerManager != null) {
            audioListenerManager.stopListening();
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }

    @Override
    public void onBackPressed() {
        // Let JS handle back navigation; if JS returns false we exit
        webView.evaluateJavascript(
            "(function(){ if(window.onNativeBack) return window.onNativeBack(); return false; })()",
            new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (!"true".equals(value)) {
                        MainActivity.super.onBackPressed();
                    }
                }
            }
        );
    }

    // ─── System UI ────────────────────────────────────────────────────────────

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    // ─── WebView Setup ────────────────────────────────────────────────────────

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);

        // Enable remote debugging on debug builds (Chrome DevTools)
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
            Log.d(TAG, "WebView remote debugging ENABLED (debug build)");
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded: " + url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                    String description, String failingUrl) {
                Log.e(TAG, "WebView error " + errorCode + ": " + description
                    + " — " + failingUrl);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.d(TAG, "[JS " + msg.messageLevel() + "] "
                    + msg.message()
                    + "  @" + msg.sourceId() + ":" + msg.lineNumber());
                return true;
            }
        });

        webView.addJavascriptInterface(new SFMBridge(), "SFMBridge");
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_RECORD_AUDIO) {
            final boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            webView.post(new Runnable() {
                @Override
                public void run() {
                    if (granted) {
                        webView.evaluateJavascript(
                            "window.onPermissionGranted && window.onPermissionGranted()", null);
                    } else {
                        webView.evaluateJavascript(
                            "window.onPermissionDenied && window.onPermissionDenied()", null);
                    }
                }
            });
        }
    }

    // ─── JavaScript Bridge ────────────────────────────────────────────────────

    public class SFMBridge {

        /** Request microphone permission. */
        @JavascriptInterface
        public void requestAudioPermission() {
            Log.d(TAG, "[Bridge] requestAudioPermission");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQ_RECORD_AUDIO
                    );
                    return;
                }
            }
            // Already granted (or pre-M)
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(
                        "window.onPermissionGranted && window.onPermissionGranted()", null);
                }
            });
        }

        /** Returns true if RECORD_AUDIO permission is held. */
        @JavascriptInterface
        public boolean hasAudioPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }

        /**
         * Start STT listening for up to durationMs milliseconds.
         * Callbacks: window.onListenResult(text), window.onListenError(msg),
         *            window.onAmplitude(0-100)
         */
        @JavascriptInterface
        public void startListening(final int durationMs) {
            Log.d(TAG, "[Bridge] startListening " + durationMs + "ms");
            audioListenerManager.startListening(durationMs,
                new AudioListenerManager.Callback() {
                    @Override
                    public void onResult(final String text) {
                        jsCallback("window.onListenResult", escapeJs(text));
                    }

                    @Override
                    public void onError(final String error) {
                        jsCallback("window.onListenError", escapeJs(error));
                    }

                    @Override
                    public void onAmplitude(final int amp) {
                        webView.post(new Runnable() {
                            @Override
                            public void run() {
                                webView.evaluateJavascript(
                                    "window.onAmplitude && window.onAmplitude(" + amp + ")",
                                    null);
                            }
                        });
                    }
                });
        }

        /** Stop listening early. */
        @JavascriptInterface
        public void stopListening() {
            Log.d(TAG, "[Bridge] stopListening");
            audioListenerManager.stopListening();
        }

        /** Persist a JSON result entry to recents. */
        @JavascriptInterface
        public void saveResult(String jsonEntry) {
            storageManager.saveResult(jsonEntry);
        }

        /** Get all recents as a JSON array string. */
        @JavascriptInterface
        public String getRecents() {
            return storageManager.getRecents();
        }

        /** Delete a single recent by title. */
        @JavascriptInterface
        public void deleteRecent(String title) {
            storageManager.deleteRecent(title);
        }

        /** Clear all stored recents. */
        @JavascriptInterface
        public void clearRecents() {
            storageManager.clearRecents();
        }

        /** Show a native Android toast. */
        @JavascriptInterface
        public void showToast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /** Returns true if the device has an active network connection. */
        @JavascriptInterface
        public boolean isOnline() {
            ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }

        /** Returns the app versionName string. */
        @JavascriptInterface
        public String getAppVersion() {
            try {
                return getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1.0";
            }
        }

        /** Returns the Android SDK int as a string (e.g. "21"). */
        @JavascriptInterface
        public String getApiLevel() {
            return String.valueOf(Build.VERSION.SDK_INT);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Fire a JS callback with a quoted string argument, on the UI thread. */
    private void jsCallback(final String fn, final String quotedArg) {
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(
                    fn + " && " + fn + "(\"" + quotedArg + "\")", null);
            }
        });
    }

    /** Escape a string for safe embedding in a JS double-quoted string literal. */
    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
