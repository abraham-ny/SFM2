# Keep JavaScript interface bridge methods
-keepclassmembers class com.vitalsoft.sfm.MainActivity$SFMBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep SpeechRecognizer listener
-keep class android.speech.** { *; }

# Keep WebView-related
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
