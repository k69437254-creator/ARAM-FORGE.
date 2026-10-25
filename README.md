# ARAM FORGE — Android developer workspace

An Android project with an Arabic RTL, blood-red developer workspace UI. The app ships its HTML/CSS/JS interface locally in the APK while connecting to online services when needed.

## Included in this prototype

- Dark blood-red interface with right-to-left Arabic navigation and a mobile bottom navigation bar.
- Code editor for text/code files; open files through Android's document picker, save with the system save dialog, run basic code inspection, and run JavaScript in a sandboxed iframe with network connections blocked.
- Internal GitHub Explorer backed by the public GitHub REST API: live public repository data, repository search, file-tree browsing, source preview, copy source, and opening GitHub's full site in the in-app browser.
- Internal web browser activity with HTTPS error rejection, navigation history, safe HTTP/HTTPS URL checks, and Android DownloadManager for downloads.
- Termux bridge with a native confirmation dialog before commands are sent to Termux. Commands require Termux to be installed, the `com.termux.permission.RUN_COMMAND` permission to be granted, and `allow-external-apps=true` in `~/.termux/termux.properties`.
- Local tools: JSON formatter, Base64 encode/decode, URL encoding, SHA-256, regex matching, AES-256-GCM encryption/decryption, with PBKDF2 key derivation.
- ZIP extraction through Android's document picker. The extractor rejects traversal paths and limits expansion to 250 MB / 5,000 entries.
- Session-only OpenAI-compatible chat integration. The API key is kept in JavaScript memory and is not persisted in local storage.
- Settings for theme, UI density, base font size, and motion preference; developer page with a hidden Telegram address behind a button.
- GitHub Actions workflow that builds a debug APK and uploads it as a workflow artifact.

## Services that require further configuration

This prototype is an honest first implementation, not a claim that every advanced service is already production-ready.

- **GitHub:** Public repository search and source browsing work with the unauthenticated GitHub API. GitHub applies request limits. Private repositories and write actions (push, issue creation, pull requests) need a proper GitHub App/OAuth flow and secure backend; no personal token is embedded in this project.
- **AI:** provide a compatible API endpoint, model ID, and API key on the AI screen. Browser CORS policy depends on the chosen provider. A production release should route requests through a backend rather than expose API credentials from a client app.
- **VPN:** no fake “connected” state is shown. A real VPN requires an Android `VpnService`, a trusted provider or owned servers, tunnel configuration, consent flow, and leak testing.
- **Files:** text/code files are readable in the editor. PDF/DOCX/XLSX renderers and more archive formats require additional libraries and tests; this prototype does not claim to support every file format.
- **Code testing:** the built-in inspection is heuristic, not a full static-analysis engine. The JavaScript sandbox blocks network access but is not a replacement for an isolated remote runner for other languages.

## Build an APK with GitHub Actions

1. Upload the entire project folder contents to a GitHub repository.
2. Ensure the default branch is `main` or `master`.
3. Open **Actions** and run **Build ARAM FORGE APK**, or push a commit.
4. Open the completed workflow run and download the `aram-forge-debug-apk` artifact.
5. Extract the artifact ZIP; it contains `app-debug.apk`.

The current Gradle build targets Android API 35, Java 17, and Android API 24+ devices. The GitHub workflow builds a debug APK with the `.debug` application ID suffix.

## Termux setup

1. Install Termux from its official project source.
2. Open Android Settings → Apps → ARAM FORGE → Permissions and grant the Termux command permission if it appears as an additional permission.
3. In Termux, configure `allow-external-apps=true` in `~/.termux/termux.properties` and restart Termux.
4. Use Shell Lab only with commands that you understand and trust. Every command requires confirmation inside ARAM FORGE, then executes in the actual Termux environment.

## Implementation notes

- The local UI is loaded with AndroidX `WebViewAssetLoader`, not `file://`, so it uses an HTTPS-like local origin for a cleaner same-origin security model.
- The main app WebView only renders local app assets. Remote sites open in a separate internal `BrowserActivity`, which does not have the JavaScript bridge exposed.
- The project does not contain private API keys or server credentials.
