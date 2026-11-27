# Release Secrets & Build Flow

> 1.1.0 (`versionCode 9`, commit `b949621`, Supabase) is live on Play. 2.0 ships as a beta first, then the same build is promoted.
>
> - **Secrets** live in the repo's git-ignored **`secrets/`** folder on the development PC. Never commit them.
> - **Server secrets** are pasted into the hosting provider's environment-variables page.
> - **Old PC** only pulls `main`, signs with the Android Studio wizard and uploads to Play. There is no signing config in Gradle.

---

## 1. The `secrets/` folder

`/secrets/` is in the root `.gitignore`. Check with `git check-ignore -v secrets/release.env`.

```
secrets/
  release.env                    # values below
  firebase-service-account.json
  upload.jks                     # copy of the upload keystore from the old PC
```

Copy the whole folder to a USB stick as the offline backup. Losing the upload keystore means a Play Console upload-key reset, which takes days.

Template for `secrets/release.env` (fill it in on the development PC only):

```properties
# --- Android build: copy into android/local.properties on the old PC ---
GOOGLE_WEB_CLIENT_ID=
RELEASE_API_BASE_URL=https://
REVENUECAT_PUBLIC_KEY=                 # public goog_ key

# --- Upload keystore: typed into the Generate Signed App Bundle wizard ---
KEYSTORE_FILE=upload.jks
KEY_ALIAS=
KEYSTORE_PASSWORD=
KEY_PASSWORD=

# --- Server: paste into the host's environment variables ---
ENVIRONMENT=production
SECRET_KEY=
GOOGLE_CLIENT_IDS=                 # same value as GOOGLE_WEB_CLIENT_ID
REVENUECAT_SECRET_KEY=                # new sk_ key
REVENUECAT_WEBHOOK_AUTH=
# FIREBASE_CREDENTIALS_JSON = the contents of firebase-service-account.json
# DATABASE_URL comes from the host's PostgreSQL database

# --- Account import only ---
SUPABASE_DB_URL=
```

## 2. Where each value comes from

| Value | Get it from |
|---|---|
| Upload keystore, alias, passwords | Old PC: `Get-ChildItem C:\Users -Recurse -Include *.jks,*.keystore -ErrorAction SilentlyContinue`. The old project's `.idea\workspace.xml` → `GenerateSignedApkSettings` shows the file and alias. **Missing = start a Play Console upload-key reset now.** |
| `GOOGLE_WEB_CLIENT_ID` | Google Cloud → APIs & Services → Credentials → the OAuth 2.0 *Web* client 1.1.0 used. Also check that the Android client lists both the upload-key and the Play App Signing SHA-1 (Play Console → Test and release → App integrity). |
| `RELEASE_API_BASE_URL` | The backend service's public HTTPS domain on the host, with `https://`. See `backend/README.md` → Deploying. |
| `REVENUECAT_PUBLIC_KEY` | RevenueCat → project → API keys → the public Google Play key (`goog_…`). Same as 1.1.0. |
| `REVENUECAT_SECRET_KEY` | RevenueCat → project → API keys → **+ New secret API key**, API version **V1** (the backend calls `/v1/subscribers`). A new key does not revoke the old one. |
| `SECRET_KEY`, `REVENUECAT_WEBHOOK_AUTH` | Generate each: `python -c "import secrets; print(secrets.token_urlsafe(48))"` |
| Firebase JSON | Firebase console → project `cozy-doubling-17db0` → Project settings → Service accounts → **Generate new private key**. Old keys keep working. |
| `SUPABASE_DB_URL` | Supabase dashboard → **Connect → Session pooler**, with the DB password filled in. |

`android/app/google-services.json` is committed on purpose: it is client config, restricted by package name and SHA-1.

## 3. Release build on the old PC

1. `git pull` on `main` and open the **`android/`** folder in Android Studio (1.1.0's Gradle root was the repo root).
2. In `android/local.properties` (git-ignored), below `sdk.dir`, add the three Android build lines from `release.env`:
   ```properties
   GOOGLE_WEB_CLIENT_ID=...
   RELEASE_API_BASE_URL=https://...
   REVENUECAT_PUBLIC_KEY=goog_...
   ```
   `preReleaseBuild` stops the release build if one is missing or the URL is not `https://`.
3. **Build → Generate Signed App Bundle or APK → Android App Bundle → Next**: pick the keystore, enter the alias and passwords → **Next** → **release** → **Create**.
4. Upload `android/app/release/app-release.aab` to a Play **testing** track first.

## 4. After the full release

Keep RevenueCat, Firebase project `cozy-doubling-17db0`, the Google OAuth clients and the upload keystore; 2.0 uses them.

A few weeks after 2.0 is in production:
1. Delete the Supabase project. 1.1.0 clients stop working; their data was imported.
2. Revoke the old RevenueCat `sk_` key and Firebase key that Supabase used. Remove any webhook still pointing at Supabase.
3. Delete `SUPABASE_DB_URL` from `release.env`.

## 5. Before every push

This must print nothing:

```powershell
git grep -nE "BEGIN (RSA )?PRIVATE KEY|(^|[^A-Za-z])sk_[A-Za-z0-9]{10,}|store[P]assword|service[_]role"
```

The bracketed letters keep the command from matching its own text. `:app:preReleaseBuild` failing on the development PC without release values is expected.
