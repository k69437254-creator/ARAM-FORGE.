package com.aramforge.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class BrowserActivity extends Activity {
    public static final String EXTRA_URL = "com.aramforge.app.EXTRA_URL";
    private WebView webView;
    private EditText address;
    private ProgressBar progress;
    private TextView pageStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.rgb(13, 8, 10));
        getWindow().setNavigationBarColor(Color.rgb(13, 8, 10));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 8, 10));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(9), dp(10), dp(8));
        toolbar.setBackgroundColor(Color.rgb(23, 16, 20));

        Button back = button("‹");
        back.setContentDescription("رجوع");
        back.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) webView.goBack();
            else finish();
        });
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(42)));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.rgb(247, 237, 240));
        address.setHintTextColor(Color.rgb(154, 132, 142));
        address.setHint("https://example.com");
        address.setTextSize(13);
        address.setTextDirection(View.TEXT_DIRECTION_LTR);
        address.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        address.setPadding(dp(12), 0, dp(12), 0);
        address.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(104, 45, 64)));
        address.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI | android.text.InputType.TYPE_CLASS_TEXT);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        toolbar.addView(address, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button go = button("اذهب");
        go.setTextSize(12);
        go.setOnClickListener(v -> navigateFromAddress());
        toolbar.addView(go, new LinearLayout.LayoutParams(dp(57), dp(42)));
        root.addView(toolbar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(227, 41, 75)));
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.rgb(227, 41, 75)));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 8, 10));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onReceivedTitle(WebView view, String title) {
                pageStatus.setText(title == null || title.isEmpty() ? "المتصفح الداخلي" : title);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handleUrl(url); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                address.setText(url);
                pageStatus.setText("جارٍ التحميل…");
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                address.setText(url);
                pageStatus.setText("تم تحميل الصفحة");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                Toast.makeText(BrowserActivity.this, "تم رفض اتصال HTTPS غير الموثوق.", Toast.LENGTH_LONG).show();
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    Uri uri = Uri.parse(url);
                    if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                        Toast.makeText(BrowserActivity.this, "نوع رابط التنزيل غير مدعوم.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DownloadManager.Request request = new DownloadManager.Request(uri);
                    request.setMimeType(mimeType);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                    request.setDescription("تنزيل من متصفح ARAM FORGE");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                    DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (manager != null) manager.enqueue(request);
                    Toast.makeText(BrowserActivity.this, "بدأ التنزيل إلى مجلد Downloads.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(BrowserActivity.this, "تعذر بدء التنزيل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));

        pageStatus = new TextView(this);
        pageStatus.setText("متصفح ARAM FORGE");
        pageStatus.setTextColor(Color.rgb(172, 148, 158));
        pageStatus.setTextSize(10);
        pageStatus.setPadding(dp(12), dp(5), dp(12), dp(5));
        root.addView(pageStatus);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        setContentView(root);

        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateFromAddress();
                return true;
            }
            return false;
        });
        String initial = getIntent().getStringExtra(EXTRA_URL);
        loadUrlSafely(initial == null ? "https://github.com" : initial);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setPadding(dp(5), 0, dp(5), 0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(179, 25, 57)));
        return b;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private boolean handleUrl(String raw) {
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) return false;
        if ("http".equalsIgnoreCase(scheme)) {
            webView.loadUrl(raw.replaceFirst("(?i)^http:", "https:"));
            return true;
        }
        if ("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "لا يوجد تطبيق للتعامل مع الرابط.", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        if ("intent".equalsIgnoreCase(scheme)) {
            try {
                Intent intent = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "تم رفض رابط التطبيق غير المدعوم.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return true;
    }

    private void navigateFromAddress() {
        String raw = address.getText() == null ? "" : address.getText().toString().trim();
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        loadUrlSafely(raw);
    }

    private void loadUrlSafely(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String url = raw.trim();
        if (!url.matches("(?i)^https?://.*")) {
            if (url.contains(" ") || !url.contains(".")) url = "https://www.google.com/search?q=" + Uri.encode(url);
            else url = "https://" + url;
        }
        if (url.regionMatches(true, 0, "http://", 0, 7)) url = "https://" + url.substring(7);
        Uri uri = Uri.parse(url);
        if (uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, "أدخل عنوان HTTP أو HTTPS صالحًا.", Toast.LENGTH_SHORT).show();
            return;
        }
        address.setText(url);
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
