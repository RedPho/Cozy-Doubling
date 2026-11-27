# Cozy Doubling 2.0 — Google Play Release Readiness

> **Scope:** What has to happen before the FastAPI-backed 2.0 client replaces the Supabase-backed 1.1.0 build that is live on Google Play.
> **Production today:** commit `b949621`, `versionCode 9`, `versionName 1.1.0`, Supabase (Auth + Postgres/RLS + Realtime + Edge Functions for FCM and `sync-revenuecat`), RevenueCat + Google Play Billing, Firebase Cloud Messaging.
> **Next release:** `main`, `versionCode 10`, `versionName 2.0.0`. The client talks only to the FastAPI backend (`backend/`).
>
> **Status (2026-11-27):** all code blockers are done. Left: fill the release date in the legal pages (B6) and Play Console updates (B7). Hosting: one Oracle VM, see `deploy/oracle/README.md`. Release flow and secrets: [04_release_machine_inventory.md](04_release_machine_inventory.md).

---

## 1. Release blockers

### B1. Existing users lose everything unless accounts are migrated (`[AUTH-12]`)
**Done:** `backend/scripts/import_supabase.py` (run on the server; see `backend/README.md`). Re-run at the full release.

1.1.0 users live in Supabase (`auth.users`, `profiles`, sessions, friendships, inventory, blocks). The 2.0 client cannot see them. Without a migration, every updating user lands on an empty new account (new player tag, 0 leaves, no friends, no themes, no Supporter).
- Export Supabase tables and import them into the new database, **keeping user IDs** (RevenueCat `app_user_id` is the Supabase user ID).
- Fill `users.google_sub` from Supabase's Google identities so the first 2.0 Google sign-in resolves to the migrated account.
- Email users: import the bcrypt hashes as-is, or force a password reset (`[AUTH-10]`).
- Plan the cut-over: freeze Supabase writes, import, then roll out 2.0. Decide how long 1.1.0 keeps working against Supabase (it cannot be force-updated).

### B2. Supporter purchases are not wired in the 2.0 client
**Done:** RevenueCat is back in `data/billing/RevenueCatBillingRepository.kt`; `REVENUECAT_PUBLIC_KEY` (app) and `REVENUECAT_SECRET_KEY` (server, API V1). Left: point the RevenueCat webhook at the new server at the full release.

1.1.0 used RevenueCat (`Purchases.configure`, `logIn(userId)`, offerings, purchase, restore). The 2.0 client only has a placeholder `RemoteBillingRepository`. Before the hardening branch, it granted Supporter for free by posting `is_supporter=true`.
- Now: release builds hide Supporter passes, and the backend ignores client-asserted status in production.
- To ship subscriptions: re-integrate the RevenueCat SDK in `data/` (port from `b949621:app/src/main/java/com/grepho/cozydoubling/core/billing/BillingRepository.kt`), call `Purchases.logIn(<backend user id>)` after sign-in, point the RevenueCat webhook at `POST /api/subscriptions/webhook` with the `REVENUECAT_WEBHOOK_AUTH` secret, and set `REVENUECAT_API_KEY` so "Restore Purchases" verifies server-side.
- Existing subscribers keep their entitlement only if their user ID is preserved (B1).

### B3. Google Sign-In configuration
**Done (checked 2026-11-27):** Android OAuth clients exist for all three Play app signing SHA-1s (classical, post-quantum, previous key) and the development PC's debug key. The upload key has none; Play installs do not need it.

- Reuse the OAuth **web** client ID that Supabase used (`SUPABASE_GOOGLE_OAUTH_WEB_CLIENT_ID` in 1.1.0's `local.properties`):
  - Android: `GOOGLE_WEB_CLIENT_ID=` in `android/local.properties`.
  - Backend: `GOOGLE_CLIENT_IDS=`.
- Make sure an Android OAuth client exists for `com.grepho.cozydoubling` with the SHA-1 of the **upload key and the Play App Signing key**. Otherwise sign-in works locally but fails for Play installs.
- `bundleRelease` refuses to build without the web client ID and an `https://` API URL.

### B4. Production backend does not exist yet
**Done:** one Oracle Always Free VM (Amsterdam) with Docker Compose: API, PostgreSQL 17, Caddy HTTPS, nightly backups (`deploy/oracle/`). Alembic is not adopted for 2.0: the production database starts empty and `create_all()` builds it; add Alembic before the first schema change after 2.0.

- HTTPS/WSS domain, managed PostgreSQL, automated backups.
- Required env (the server refuses to start without them when `ENVIRONMENT=production`): `SECRET_KEY`, `DATABASE_URL`, `GOOGLE_CLIENT_IDS`, `REVENUECAT_WEBHOOK_AUTH`. Recommended: `REVENUECAT_API_KEY`, `FIREBASE_CREDENTIALS_FILE`.
- **Schema migrations:** `create_all()` + hand-written `ALTER TABLE`s in `ensure_schema_migrations()` will not scale. Adopt Alembic before the first production deploy.
- **Single process only:** room presence, safety/friends sockets and the login throttle are in-memory. Run one Uvicorn worker, or move them to Redis pub/sub first.
- Monitoring/alerting and log retention (must match the privacy policy).

### B5. Push notifications
**Done:** the server reads the service account from `FIREBASE_CREDENTIALS_JSON` (or `FIREBASE_CREDENTIALS_FILE`).

1.1.0 sent friend alerts from a Supabase Edge Function. 2.0 sends them from the backend via `firebase-admin`; they only go out when `FIREBASE_CREDENTIALS_FILE` points at a service account for the **same Firebase project** as `android/app/google-services.json`.

### B6. Legal pages
The pages served at `https://redpho.github.io/Cozy-Doubling/*.html` come from `/docs` on `main`. The 1.1.0 app and the Play listing link to them.
- Problems in the live text: it claims *"We do not store your email address in our own database"*, which is false for Supabase too. It names Supabase/RLS, describes Google-only accounts, does not mention reports, IP processing or on-device storage, and lists "aggregated usage data" that is not collected.
- `docs/play-release-readiness` fixes these. Hosting is filled in (Oracle Cloud Infrastructure, Amsterdam; logs 7 days; backups 14 days). Only `[2.0 RELEASE DATE]` is left: fill it and **merge it on release day**: merging earlier changes the policy for 1.1.0 users.
- Keep `docs/delete-account.html` linked as the Play "Delete account URL".

### B7. Play Console updates
- **Data safety form:**
  - Account info: email address and user IDs (now also email/password accounts).
  - App activity: in-app interactions and user-generated content (task text, display name).
  - Device IDs: FCM token.
  - Purchase history: via RevenueCat.
  - All encrypted in transit; deletion available in-app and on the web.
- **App access:** provide a demo email/password account (the hidden "Reviewer Access" button is gone; reviewers use "Continue with email").
- **Foreground service declaration:** `FOREGROUND_SERVICE_SPECIAL_USE` for `FocusSessionService` needs the Play Console declaration with a short video of the persistent focus notification.
- **Release:** signed with the same upload key, `versionCode 10`, `versionName 2.0.0`.

---

## 2. Strongly recommended before 2.0

| Item | Why |
|---|---|
| Password reset (`[AUTH-10]`) | Email accounts are now promoted on the Login screen. Without reset, a forgotten password means a lost account and support emails. Needs a transactional email provider. |
| Email verification (decision in `[AUTH-08]`) | Anyone can register any address today. That blocks safe password reset and lets squatters claim an email before its owner links Google (the owner can still link via a verified Google email). |
| Server-side session revocation | Logout only clears the device. JWTs stay valid for 30 days and cannot be revoked (spec `[AUTH-06]` says "invalidates it on the server"). Add a token version / session table and shorter-lived tokens with refresh. |
| Moderation tooling | Reports are stored but nobody can review them. Google Play's UGC policy expects timely action on reports, so at minimum add an admin script/dashboard and a ban flag. |
| Rate limits beyond login | Registration, friend requests and reports are unthrottled. |
| Forced-update channel | Add `GET /api/app/config` with a minimum supported version now, so future breaking backend changes can prompt 2.x users to update (1.1.0 has no such hook). |
| Release logging | Done: R8 strips `println` from release builds (`proguard-rules.pro`). |
| Google "G" button asset | Use the official branded button/mark per Google Sign-In branding guidelines. |

---

## 3. Architecture review — findings and status

| # | Finding | Severity | Status |
|---|---|---|---|
| 1 | `/api/auth/google` trusted any string; `mock:google:x:y:victim@gmail.com` returned a token for **any** account, including email/password accounts | Critical | Fixed (backend branch) |
| 2 | RevenueCat webhook ignored its `Authorization` header: anyone could grant or revoke Supporter | Critical | Fixed (backend) |
| 3 | `/api/subscriptions/sync` let the client set `is_supporter=true`, and the client's "purchase" did exactly that | Critical | Fixed (backend + client) |
| 4 | `POST /api/sessions/{random id}/finish` minted sessions, and task rewards were unbounded: unlimited leaves | High | Fixed (backend) |
| 5 | Client-chosen WebSocket `presence_key`/`name`: kick or impersonate any user, make blocks target the victim | High | Fixed (backend) |
| 6 | Client auto-registered on any failed login and revealed "Incorrect password for this account" (user enumeration) | High | Fixed (client) |
| 7 | Release build pointed at `http://10.0.2.2:8000` with cleartext enabled app-wide | High | Fixed (client build guards) |
| 8 | Development secrets as silent defaults (`SECRET_KEY`, webhook secret) | High | Fixed (production boot guard) |
| 9 | Friend push notifications never sent (no event loop in sync route; sender was a stub) | Medium | Fixed (backend) |
| 10 | Client called `DELETE /api/profile/fcm-token`, which did not exist; a device token could stay attached to the previous account | Medium | Fixed (backend) |
| 11 | DI container built in `MainActivity`; FCM/foreground-service processes got a token-less container, later replaced | Medium | Fixed (client) |
| 12 | Account deletion cleared local state even when the server call failed | Medium | Fixed (client) |
| 13 | Domain `ProfileRepository` exposed `setSupporter`/`updateLeaves`/`updateFocusMinutes` (client-writable economy) | Medium | Fixed (moved to fakes) |
| 14 | JWT (room URL) and FCM token written to logcat | Medium | Fixed (client) |
| 15 | Auth token and task drafts included in Android backups | Low | Fixed (backup rules) |
| 16 | No login brute-force protection despite spec `[AUTH-02]` | Medium | Fixed (in-memory throttle) |
| 17 | No migrations framework; in-memory realtime state blocks horizontal scaling | Medium | Open (B4) |
| 18 | No session revocation; 30-day non-revocable JWTs | Medium | Open (§2) |
| 19 | Billing integration missing in 2.0 client | Blocker | Fixed (RevenueCat, B2) |

## 4. Not verified yet
- Real Google Sign-In, RevenueCat offerings/restore and FCM can be checked locally with a debug build (`backend/.env.example`, `android/local.properties.example`).
- Not possible before the beta: sign-in with a migrated 1.1.0 account (the import runs only on the server), real Play purchases and the RevenueCat webhook on the production server.
