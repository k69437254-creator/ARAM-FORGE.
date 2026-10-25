# ARAM FORGE uses a JavaScript bridge that is referenced by WebView at runtime.
# Keep bridge methods when minification is enabled in a future release.
-keepclassmembers class com.aramforge.app.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
