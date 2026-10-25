package com.aramforge.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.SafeBrowsingResponseCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebResourceErrorCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int SAVE_TEXT_REQUEST = 1002;
    private static final int PICK_ZIP_REQUEST = 1003;
    private static final int PICK_TREE_REQUEST = 1004;
    private static final long MAX_UNZIPPED_BYTES = 250L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 5000;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingSaveText;
    private String pendingSaveMime = "text/plain";
    private String pendingSaveName = "aram-forge.txt";
    private Uri pendingZipUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.rgb(13, 8, 10));
        getWindow().setNavigationBarColor(Color.rgb(13, 8, 10));
        getWindow().getDecorView().setSystemUiVisibility(0);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 8, 10));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setLoadsImagesAutomatically(true);
        settings.setDefaultTextEncodingName("UTF-8");
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(true);
        }

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new LocalContentClient(assetLoader));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException | SecurityException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "تعذر فتح مدير الملفات على هذا الجهاز.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        setContentView(webView);
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private final class LocalContentClient extends WebViewClientCompat {
        private final WebViewAssetLoader loader;
        LocalContentClient(WebViewAssetLoader loader) { this.loader = loader; }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return loader.shouldInterceptRequest(request.getUrl());
        }

        @SuppressWarnings("deprecation")
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return loader.shouldInterceptRequest(Uri.parse(url));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isLocalAssetUrl(uri)) return false;
            openBrowser(uri.toString());
            return true;
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isLocalAssetUrl(uri)) return false;
            openBrowser(url);
            return true;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            handler.cancel();
            Toast.makeText(MainActivity.this, "تم رفض اتصال HTTPS غير الموثوق.", Toast.LENGTH_LONG).show();
        }

        private boolean isLocalAssetUrl(Uri uri) {
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())
                    && (uri.getPath() == null || uri.getPath().startsWith("/assets/"));
        }
    }

    private void openBrowser(String rawUrl) {
        String url = validateWebUrl(rawUrl);
        if (url == null) {
            Toast.makeText(this, "عنوان الويب غير صالح.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra(BrowserActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    @Nullable
    private String validateWebUrl(String raw) {
        if (raw == null) return null;
        Uri uri = Uri.parse(raw.trim());
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            return null;
        }
        return uri.toString();
    }

    private void createTextDocument(String name, String mime, String contents) {
        pendingSaveName = sanitizeFilename(name);
        pendingSaveMime = (mime == null || mime.trim().isEmpty()) ? "text/plain" : mime;
        pendingSaveText = contents == null ? "" : contents;
        runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(pendingSaveMime);
            intent.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
            try {
                startActivityForResult(intent, SAVE_TEXT_REQUEST);
            } catch (ActivityNotFoundException e) {
                pendingSaveText = null;
                Toast.makeText(this, "لا يتوفر منتقي مستندات لحفظ الملف.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "aram-forge.txt";
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "aram-forge.txt";
        return safe.length() > 120 ? safe.substring(safe.length() - 120) : safe;
    }

    private void beginZipExtraction() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try {
            startActivityForResult(intent, PICK_ZIP_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "تعذر فتح مدير الملفات.", Toast.LENGTH_LONG).show();
        }
    }

    private void beginDestinationFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_TREE_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "تعذر اختيار مجلد الوجهة.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) results = new Uri[]{uri};
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        if (requestCode == SAVE_TEXT_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveText != null) {
                Uri uri = data.getData();
                String content = pendingSaveText;
                pendingSaveText = null;
                new Thread(() -> {
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException("Unable to open destination");
                        output.write(content.getBytes(StandardCharsets.UTF_8));
                        output.flush();
                        runOnUiThread(() -> Toast.makeText(this, "تم حفظ الملف بنجاح.", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            } else {
                pendingSaveText = null;
            }
            return;
        }
        if (requestCode == PICK_ZIP_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                pendingZipUri = data.getData();
                beginDestinationFolderPicker();
            }
            return;
        }
        if (requestCode == PICK_TREE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingZipUri != null) {
                Uri treeUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri,
                            data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                } catch (SecurityException ignored) { }
                Uri zipUri = pendingZipUri;
                pendingZipUri = null;
                new Thread(() -> extractZipSafely(zipUri, treeUri)).start();
            } else {
                pendingZipUri = null;
            }
        }
    }

    private void extractZipSafely(Uri zipUri, Uri treeUri) {
        int extracted = 0;
        long totalBytes = 0L;
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, treeUri);
            if (root == null || !root.canWrite()) throw new IOException("مجلد الوجهة غير قابل للكتابة.");
            ContentResolver resolver = getContentResolver();
            try (InputStream input = new BufferedInputStream(resolver.openInputStream(zipUri));
                 ZipInputStream zip = new ZipInputStream(input)) {
                if (input == null) throw new IOException("تعذر قراءة الأرشيف.");
                ZipEntry entry;
                int entries = 0;
                byte[] buffer = new byte[16 * 1024];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ZIP_ENTRIES) throw new IOException("تجاوز الأرشيف الحد الأقصى لعدد الملفات.");
                    String entryName = entry.getName();
                    DocumentFile target = resolveSafeZipEntry(root, entryName, entry.isDirectory());
                    if (entry.isDirectory()) {
                        zip.closeEntry();
                        continue;
                    }
                    if (target == null) throw new IOException("مسار ملف غير آمن داخل ZIP.");
                    String fileName = target.getName();
                    String mime = mimeForFilename(fileName);
                    try (OutputStream raw = resolver.openOutputStream(target.getUri(), "wt");
                         BufferedOutputStream output = new BufferedOutputStream(raw)) {
                        if (raw == null) throw new IOException("تعذر إنشاء الملف: " + fileName);
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            totalBytes += read;
                            if (totalBytes > MAX_UNZIPPED_BYTES) throw new IOException("تجاوز حجم الاستخراج الحد الآمن البالغ 250 MB.");
                            output.write(buffer, 0, read);
                        }
                        output.flush();
                    }
                    extracted++;
                    zip.closeEntry();
                }
            }
            final int count = extracted;
            final long bytes = totalBytes;
            runOnUiThread(() -> Toast.makeText(this, "اكتمل الاستخراج: " + count + " ملفًا، " + (bytes / 1024) + " KB.", Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "تعذر استخراج الأرشيف." : e.getMessage();
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("تعذر استخراج ZIP")
                    .setMessage(message)
                    .setPositiveButton("حسنًا", null)
                    .show());
        }
    }

    @Nullable
    private DocumentFile resolveSafeZipEntry(DocumentFile root, String rawName, boolean directory) throws IOException {
        if (rawName == null || rawName.length() > 500 || rawName.startsWith("/") || rawName.startsWith("\\")
                || Pattern.compile("^[A-Za-z]:").matcher(rawName).find()) return null;
        String normalized = rawName.replace('\\', '/');
        String[] parts = normalized.split("/");
        List<String> safeParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..") || part.indexOf('\0') >= 0) return null;
            safeParts.add(part);
        }
        if (safeParts.isEmpty()) return null;
        DocumentFile parent = root;
        int limit = directory ? safeParts.size() : safeParts.size() - 1;
        for (int i = 0; i < limit; i++) {
            String segment = safeParts.get(i);
            DocumentFile child = parent.findFile(segment);
            if (child == null || !child.isDirectory()) {
                if (child != null) throw new IOException("تعارض اسم مجلد داخل الأرشيف: " + segment);
                child = parent.createDirectory(segment);
            }
            if (child == null) throw new IOException("تعذر إنشاء المجلد: " + segment);
            parent = child;
        }
        if (directory) return parent;
        String fileName = safeParts.get(safeParts.size() - 1);
        DocumentFile existing = parent.findFile(fileName);
        if (existing != null) {
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : "";
            int suffix = 2;
            do {
                String candidate = base + " (" + suffix++ + ")" + ext;
                existing = parent.findFile(candidate);
                if (existing == null) { fileName = candidate; break; }
            } while (suffix < 10000);
            if (existing != null) throw new IOException("تعذر إيجاد اسم ملف بديل.");
        }
        return parent.createFile(mimeForFilename(fileName), fileName);
    }

    private String mimeForFilename(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private void showTermuxPermissionHelp() {
        new AlertDialog.Builder(this)
                .setTitle("إعداد Termux مطلوب")
                .setMessage("لتنفيذ الأوامر يلزم:\n1. تثبيت Termux الرسمي.\n2. السماح لـ ARAM FORGE بإذن Run commands in Termux environment من صفحة أذونات التطبيق.\n3. تفعيل allow-external-apps=true في ملف ~/.termux/termux.properties داخل Termux.\n\nلا ترسل أوامر لا تثق بها.")
                .setPositiveButton("فتح إعدادات التطبيق", (d, w) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void executeTermuxCommandAfterConfirmation(String command) {
        if (command == null || command.trim().isEmpty()) return;
        if (command.length() > 4000) {
            Toast.makeText(this, "الأمر طويل جدًا؛ الحد 4000 حرف.", Toast.LENGTH_LONG).show();
            return;
        }
        if (getPackageManager().getLaunchIntentForPackage("com.termux") == null) {
            new AlertDialog.Builder(this).setTitle("Termux غير مثبت")
                    .setMessage("ثبت Termux من مصدره الرسمي ثم عد إلى ARAM FORGE. لن تُنفّذ الأوامر داخل التطبيق دون بيئة Termux حقيقية.")
                    .setPositiveButton("فتح صفحة المشروع", (d, w) -> openBrowser("https://github.com/termux/termux-app"))
                    .setNegativeButton("إلغاء", null).show();
            return;
        }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            showTermuxPermissionHelp();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("تأكيد تنفيذ الأمر")
                .setMessage("سيتم إرسال هذا الأمر إلى بيئة Termux الفعلية:\n\n" + command + "\n\nنفّذ فقط الأوامر التي تفهمها وتثق بها.")
                .setPositiveButton("تنفيذ في Termux", (dialog, which) -> sendCommandToTermux(command))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void sendCommandToTermux(String command) {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.setAction("com.termux.RUN_COMMAND");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "ARAM FORGE command");
            startService(intent);
            Toast.makeText(this, "تم إرسال الأمر إلى Termux. راجع جلسة Termux للنتيجة.", Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            showTermuxPermissionHelp();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر إرسال الأمر: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareText(String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
        startActivity(Intent.createChooser(send, "مشاركة من ARAM FORGE"));
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void openBrowser(String url) { runOnUiThread(() -> MainActivity.this.openBrowser(url)); }

        @JavascriptInterface
        public void openTermux() {
            runOnUiThread(() -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
                if (launch != null) startActivity(launch);
                else new AlertDialog.Builder(MainActivity.this).setTitle("Termux غير مثبت")
                        .setMessage("ثبت Termux من مصدره الرسمي لاستخدام الطرفية داخل بيئة حقيقية.")
                        .setPositiveButton("عرض المشروع", (d, w) -> openBrowser("https://github.com/termux/termux-app"))
                        .setNegativeButton("إلغاء", null).show();
            });
        }

        @JavascriptInterface
        public void runTermuxCommand(String command) {
            runOnUiThread(() -> executeTermuxCommandAfterConfirmation(command));
        }

        @JavascriptInterface
        public void saveText(String filename, String mimeType, String contents) {
            createTextDocument(filename, mimeType, contents);
        }

        @JavascriptInterface
        public void extractZip() { runOnUiThread(MainActivity.this::beginZipExtraction); }

        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("ARAM FORGE", text == null ? "" : text));
            });
        }

        @JavascriptInterface
        public void shareText(String text) { runOnUiThread(() -> MainActivity.this.shareText(text)); }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
