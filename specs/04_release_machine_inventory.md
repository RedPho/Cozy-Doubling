# Release Secrets & Build Flow

> The 1.1.0 Play build (`versionCode 9`, commit `b949621`, Supabase backend) was built and signed on the **old PC**.
>
> For 2.0:
> - Secrets are collected and created on the **development PC**, in a git-ignored secrets file.
> - The **old PC** only pulls `main`, signs with the upload keystore, and uploads to Play.
> - Server secrets go into the **hosting provider's settings**, never onto the build PC.
>
> Record only *found / created / missing* in this file, **never a value**.

Signing uses Android Studio's **Build → Generate Signed App Bundle** wizard. There is deliberately no signing config in Gradle.

---

## 1. Where secrets live

- **Development PC.** Keep one secrets file **outside the repository**, for example `%USERPROFILE%\cozy-secrets\cozy.env` and the Firebase JSON next to it, so nothing in the repo (tools included) reads or commits it by accident.
  - `.env`, `backend/.env*`, `*.jks`, `*.keystore`, `local.properties` and service-account JSON are git-ignored as a safety net.
- **Password manager.** Holds the upload keystore file and its passwords, plus an offline backup. Losing the upload key means a Play Console upload-key reset, which takes days.
- **Hosting provider.** Holds every backend secret as environment variables, entered through its settings page.
- **Old PC (build).** Holds the keystore and `android/local.properties`, which contains no secrets.

## 2. Inventory

| # | Item | Get it from | Goes to | Secret | Status |
|---|---|---|---|---|---|
| S1 | **Upload keystore** + store password, key alias, key password | Old PC: `Get-ChildItem C:\Users -Recurse -Include *.jks,*.keystore -ErrorAction SilentlyContinue`. The old project's `.idea\workspace.xml` → `GenerateSignedApkSettings` (`KEY_STORE_PATH`, `KEY_ALIAS`) shows which file and alias the wizard used. Passwords: password manager, or Windows Credential Manager if "Remember passwords" was ticked. **Missing = start a Play Console upload-key reset now.** | Password manager + offline backup. Used by the wizard on the old PC. | **Yes** | |
| S2 | `GOOGLE_WEB_CLIENT_ID` | Google Cloud → Credentials → OAuth *Web* client. This is the client 1.1.0 used as `SUPABASE_GOOGLE_OAUTH_WEB_CLIENT_ID`. | Build: `android/local.properties`. Server: `GOOGLE_CLIENT_IDS` | No | |
| S3 | Android OAuth client SHA-1s | Google Cloud → Credentials → Android client for `com.grepho.cozydoubling`. Compare with Play Console → App integrity. | Both the **upload-key** and **Play App Signing** SHA-1 must be registered | No | |
| S4 | `RELEASE_API_BASE_URL` | Hosting provider (the `https://` API host) | Build: `android/local.properties` | No | |
| S5 | `REVENUECAT_APIKEY` (public `goog_…` SDK key) | RevenueCat → Project → API keys. Same key as 1.1.0. | Build: `android/local.properties`, once RevenueCat is re-integrated in Android | No | |
| S6 | `SECRET_KEY` | Generate: `python -c "import secrets; print(secrets.token_urlsafe(48))"` | Server env | **Yes** | |
| S7 | `DATABASE_URL` | Hosting provider's PostgreSQL. A `postgres://` URL is fine. | Server env | **Yes** | |
| S8 | `REVENUECAT_API_KEY` (secret `sk_…`) | RevenueCat → API keys → **create a new** secret key | Server env | **Yes** | |
| S9 | `REVENUECAT_WEBHOOK_AUTH` | Generate the same way as S6 | Server env, and the RevenueCat webhook's Authorization header | **Yes** | |
| S10 | Firebase service-account JSON | Firebase console, project `cozy-doubling-17db0` → Project settings → Service accounts → **generate a new key** | Upload to the server; path in `FIREBASE_CREDENTIALS_FILE` | **Yes** | |
| S11 | `SUPABASE_DB_URL` | Supabase dashboard → **Connect → Session pooler** (includes the DB password) | Env of the importer run only (`backend/scripts/import_supabase.py`) | **Yes** | |
| S12 | Play Console facts | App integrity (signing and upload-key SHA-1/SHA-256), Data safety, App access, FGS declarations, release notes | Release checklist | No | |
| S13 | 1.1.0 artifacts (optional) | Play Console → App bundle explorer: download the 1.1.0 AAB and `mapping.txt` for crash deobfuscation | Keep with the release records | No | |

`android/app/google-services.json` is committed on purpose. It is client config, not a secret; its API key is restricted by package name and signing SHA-1.

## 3. New credentials without breaking 1.1.0

Everything for 2.0 is created **alongside** what 1.1.0 uses, so 1.1.0 keeps working until release day:

- **RevenueCat**
  - A new `sk_` key does not revoke the old one.
  - Products, entitlement (`Cozy Doubling Supporter Pass`), offerings and the public `goog_` key stay the same, and 2.0 reuses them.
  - Webhook: add a second one for the new API if the plan allows it; otherwise point the existing webhook at the new API on release day.
  - The 2.0 backend also verifies entitlements through the REST API on restore, so the webhook switch is not time-critical.
- **Firebase.** A new service-account key does not invalidate existing keys.
- **Google OAuth.** No new client is needed; 2.0 uses the same web client ID. Only check the SHA-1s (S3).
- **Supabase.** Until release day it is only read: `--check` and `--dry-run` of the importer.

## 4. Release build on the old PC

1. `git pull` on `main` and open the **`android/`** folder in Android Studio. 1.1.0's Gradle root was the repo root, so this layout differs.
2. Create `android/local.properties` (git-ignored), copying the S2/S4/S5 values from the development PC:
   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   GOOGLE_WEB_CLIENT_ID=<S2>
   RELEASE_API_BASE_URL=https://<S4>
   # once RevenueCat is re-integrated:
   # REVENUECAT_APIKEY=<S5>
   ```
   Legal page URLs come from `local.defaults.properties`. Do not copy the old ones.
3. Run `.\gradlew.bat testDebugUnitTest`. Then **Build → Generate Signed App Bundle → release** with the S1 keystore.
   - The `preReleaseBuild` check stops the build if `RELEASE_API_BASE_URL` is not `https://` or `GOOGLE_WEB_CLIENT_ID` is empty.
4. Verify the key with `keytool -printcert -jarfile app\release\app-release.aab`. The SHA-1 must equal the Play Console upload-key SHA-1 (S12).
5. Upload to the **internal testing** track first. Full release steps are in [03_play_release_readiness.md](03_play_release_readiness.md).

## 5. After the release

**Keep:** RevenueCat, Firebase project `cozy-doubling-17db0`, the Google OAuth clients and the upload keystore. 2.0 uses all of them.

**Retire, once 2.0 adoption is high (a few weeks after rollout):**
1. Delete the Supabase project. 1.1.0 clients stop working at that point, and their data was migrated at cut-over. The privacy policy states that the old data is deleted.
2. Revoke the RevenueCat `sk_` key and the Firebase service-account key that the Supabase edge functions used. Remove any old webhook that still points at Supabase.
3. Delete S11 (`SUPABASE_DB_URL`) from the secrets file and the server.

## 6. Development PC rules
- Secrets only in the secrets file outside the repo, or in the password manager. Never in tracked files, commit messages, PRs or chat logs.
- Before every push, this must return nothing:
  ```powershell
  git grep -nE "BEGIN (RSA )?PRIVATE KEY|(^|[^A-Za-z])sk_[A-Za-z0-9]{10,}|store[P]assword|service[_]role"
  ```
  The bracketed letters keep the command from matching its own text in this file.
- `:app:preReleaseBuild` failing here without a release `local.properties` is expected.
