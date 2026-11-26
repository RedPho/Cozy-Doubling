# Release Machine Inventory (old PC)

> The 1.1.0 Play build (`versionCode 9`, commit `b949621`, Supabase backend) was built and signed on the **old PC**. Everything secret stays there, or in the password manager. **Never commit or copy secret values to this repository or to the development PC.**
> In this file, record only whether each item was **found / missing / recreated**, never the value itself.

Release signing uses Android Studio's **Build → Generate Signed App Bundle** wizard on the old PC. There is deliberately no signing config in Gradle.

---

## 1. Collect on the old PC

Run the searches in PowerShell **on the old PC**.

| # | Item | How to find it | Used for | Secret | Status |
|---|---|---|---|---|---|
| A1 | **Upload keystore** + store password, key alias, key password | `Get-ChildItem C:\Users -Recurse -Include *.jks,*.keystore -ErrorAction SilentlyContinue`. The old project's `.idea\workspace.xml` → `GenerateSignedApkSettings` (`KEY_STORE_PATH`, `KEY_ALIAS`) shows which file and alias the wizard used. Passwords: password manager, or Windows Credential Manager if "Remember passwords" was ticked. | Signing 2.0. **Missing = start a Play Console upload-key reset now (takes days).** | Yes | |
| A2 | Old `local.properties` (repo root of the 1.1.0 checkout) | Open the file | `SUPABASE_GOOGLE_OAUTH_WEB_CLIENT_ID` → reuse as `GOOGLE_WEB_CLIENT_ID`. `REVENUECAT_APIKEY` (public `goog_…` SDK key). `SUPABASE_URL` / `SUPABASE_ANON_KEY` for the data export. | Mixed | |
| A3 | Last Play artifacts | `app\release\app-release.aab`, `app\release\output-metadata.json`, `app\build\outputs\mapping\release\mapping.txt` (also downloadable from Play Console) | Rollback reference, 1.1.0 crash deobfuscation | No | |
| A4 | Supabase CLI project | A `supabase\` folder (`config.toml`, `migrations\`, `functions\`, `.env*`) near the old checkout | Schema, RPCs, edge functions `send-friend-notification` and `sync-revenuecat` | `.env` yes | |
| A5 | Supabase data + function source (if A4 is absent) | `supabase db dump --schema public,auth` and `supabase functions download send-friend-notification` (same for `sync-revenuecat`). RPCs used by 1.1.0: `accept_friend, block_user, buy_item, delete_own_account, equip_item, finish_session, reject_friend, report_user, unequip_theme`. View: `friend_stories`. | Account migration (`[AUTH-12]`) | **Yes (user data)** | |
| A6 | Supabase edge-function secret **names** | Dashboard → Edge Functions → Secrets | Know which keys to recreate for the new backend | Yes | |
| A7 | Firebase service account | Firebase console, project `cozy-doubling-17db0` → Project settings → Service accounts → **generate a new key** for the new backend | `FIREBASE_CREDENTIALS_FILE` on the server | Yes | |
| A8 | RevenueCat | Dashboard: app, entitlement `Cozy Doubling Supporter Pass`, offering/package IDs, Play service-credentials status. **Create** a secret `sk_` key and a webhook auth token. | `REVENUECAT_API_KEY`, `REVENUECAT_WEBHOOK_AUTH`, offerings mapping | Yes | |
| A9 | Google Cloud OAuth clients | Credentials page: the Web client (= A2) and the Android client for `com.grepho.cozydoubling` with SHA-1s | Backend `GOOGLE_CLIENT_IDS`. Both the **upload-key** and **Play App Signing** SHA-1s must be registered. | No | |
| A10 | Play Console facts | Setup → App integrity (Play App Signing enrolled? SHA-1/SHA-256 of the app-signing and upload keys). Also: Data safety, App access, FGS declarations, release notes. | Fingerprint check, listing updates | No | |
| A11 | Anything else | `$env:USERPROFILE\.gradle\gradle.properties`; `.env*` files; private-key JSON files: `Get-ChildItem C:\Users -Recurse -Filter *.json -ErrorAction SilentlyContinue \| Select-String '"private_key"' -List` | Nothing forgotten | Likely | |

`android/app/google-services.json` is committed on purpose. It is a client config, not a secret; its API key is restricted by package name and signing SHA-1.

**Backups:** keep the keystore and passwords in the password manager **and** on an offline medium. Losing the upload key means a Play Console key reset.

---

## 2. Release build on the old PC (Android Studio wizard)

1. Clone or pull `main` and open the **`android/`** folder in Android Studio. 1.1.0's Gradle root was the repo root.
2. Create `android/local.properties` (git-ignored) on the old PC:
   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   GOOGLE_WEB_CLIENT_ID=<A2: old SUPABASE_GOOGLE_OAUTH_WEB_CLIENT_ID>
   RELEASE_API_BASE_URL=https://<production api host>
   # once RevenueCat is re-integrated:
   # REVENUECAT_APIKEY=<A2: goog_…>
   ```
   Do not copy the `TERMS_OF_SERVICE_URL` / `PRIVACY_POLICY_URL` lines from the old file. Defaults come from `local.defaults.properties`.
3. Run the unit tests (`.\gradlew.bat testDebugUnitTest`). Then **Build → Generate Signed App Bundle → release** with the A1 keystore. The `preReleaseBuild` check stops the build if `RELEASE_API_BASE_URL` is not `https://` or `GOOGLE_WEB_CLIENT_ID` is empty.
4. Confirm the same upload key: `keytool -printcert -jarfile app\release\app-release.aab`. The SHA-1 must equal the Play Console upload-key SHA-1 (A10).
5. Upload to the **internal testing** track first. Full release steps are in [03_play_release_readiness.md](03_play_release_readiness.md).

## 3. Development PC rules
- No keystore, no `local.properties` with production values, no service-account JSON, no Supabase dumps.
- `:app:preReleaseBuild` is expected to **fail** on the development PC.
- Before every push, `git grep -nE "BEGIN (RSA )?PRIVATE KEY|sk_[A-Za-z0-9]{10,}|storePassword|service_role"` must return nothing.
