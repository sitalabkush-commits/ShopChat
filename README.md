# ShopManager Chat

Android (Kotlin, Jetpack Compose, Material 3, minSdk 26) remote assistant for the
desktop shop-management app. It talks to the desktop app's HTTP API:

- `POST http://<pc-ip>:8787/chat` - header `Authorization: Bearer <token>`,
  body `{"question": "...", "history": [{"role":"user|assistant","content":"..."}]}`
- `GET /health`

No token or address is stored in the source. Both are entered in the app and kept in
`EncryptedSharedPreferences`.

## Features
Setup screen with Test connection - chat with Room-backed history (last 10 messages
sent as `history`, Clear chat) - voice input (English / Hindi / Bengali) - quick
question chips - clear errors for no network, 401, server off, timeout (OkHttp 60 s
timeouts with retry) - plain-text answers with `**bold**` and Markdown tables - Indian
number grouping (`₹12,34,567`) applied to currency amounts in answers - light/dark theme.

## Build locally
1. Install Android Studio (Koala or newer) with JDK 17.
2. Open this folder. The Gradle wrapper isn't included; generate it once with
   `gradle wrapper --gradle-version 8.7` (needs Gradle installed), or let Android Studio use its bundled Gradle.
3. Run on a device, or `gradle :app:assembleDebug`.

## Connect to your PC
1. Start the shop app so the API listens on your PC (port 8787) and allow it through
   Windows Firewall (private networks).
2. Phone on the **same Wi-Fi**, or both devices on **Tailscale**.
3. In the app: enter the PC's IP (e.g. `192.168.1.20`, or its `100.x` Tailscale IP),
   port `8787`, and the token. Tap **Test connection**, then **Save**.

### About cleartext HTTP
Android's `network_security_config` cannot match IP ranges (no CIDR), so the XML permits
cleartext at platform level and the app itself enforces the rule in `ShopApi.kt`:
`http://` is allowed only for `192.168.x.x`, `10.x.x.x` and Tailscale `100.64.0.0/10`
(the range Tailscale actually uses). Anything else over HTTP is refused with a clear
message. `https://` addresses are always allowed.

## Signed release APK with GitHub Actions
1. Create a keystore once (keep it safe, never commit it):
   `keytool -genkeypair -v -keystore release.jks -alias shopchat -keyalg RSA -keysize 2048 -validity 10000`
2. Encode it: `base64 -w0 release.jks` (macOS: `base64 -i release.jks`).
3. In the GitHub repo: Settings -> Secrets and variables -> Actions, add:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
4. Run **Actions -> Build signed release APK -> Run workflow**, or push a tag such as
   `git tag v1.0.0 && git push --tags`. The APK is uploaded as a workflow artifact
   (and attached to the Release for tags).
5. Install the APK on your phone (allow "install unknown apps").

Without the env vars, `assembleRelease` still works locally but produces an unsigned APK.

## Adjusting to your server
- Answer text is read from the JSON keys `answer`, `response`, `reply`, `text`, `message`
  or `result` (raw body if none) - see `ShopApi.extractText`.
- History items are `{role, content}`; change `ShopApi.chat` if your server expects another shape.
