# 🌿 Cozy Doubling — Python Backend

A robust, production-ready Python backend for **Cozy Doubling** built with **FastAPI**, **SQLAlchemy**, **Pydantic**, and **WebSockets**.

Designed strictly according to the product specifications in `specs/00_inventory.md` and `specs/01_product_backlog.md`.

---

## 🚀 Quick Start

### 1. Install Dependencies
```bash
pip install -r backend/requirements.txt
```

### 2. Run the Server
```bash
python backend/run.py
```
Or with Uvicorn:
```bash
uvicorn app.main:app --app-dir backend --host 0.0.0.0 --port 8000 --reload
```
API Documentation (Swagger UI) is available at: [http://localhost:8000/docs](http://localhost:8000/docs)

### 3. Run the Test Suite
```bash
python backend/run_tests.py
```
Or using pytest:
```bash
pytest backend/tests -v -c backend/pytest.ini
```

---

## 🏛 Architecture & Features

### 1. Authentication & Account Lifecycle (`/api/auth`)
- **Email & Password Login:** Case-insensitive, generic error messaging against user enumeration, bcrypt password hashing.
- **Google Sign-In:** One-tap / token verification with auto-provisioning.
- **Unique Player Tag:** Collision-free 8-character tag from charset `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` (excludes ambiguous `0, 1, I, O`).
- **5-Step Ordered Cascade Deletion:** Fully deletes inventory, friendships, sessions, blocks, reports, profile, and user records.

### 2. Live Focus Room & Body Doubling (`/api/sessions` & `/ws/room`)
- **Presence & Broadcasting:** Real-time presence updates, active task text broadcasting, completed/total task counts.
- **Capacity Limits:** Configurable room capacity ceiling (`ROOM_CAPACITY_LIMIT`).
- **Real-Time Block Filtering:** Automatically hides blocked participants and suppresses messages between blocked users.

### 3. Session Completion & Leaf Economy (`/api/sessions`)
- **Server-Derived Duration:** Duration is computed strictly on the server: `duration_minutes = (end_time - start_time) / 60`.
- **Anti-Cheat Leaf Formula:**
  $$\text{earned\_leaves} = (\text{duration\_minutes} \times 1) + (\text{tasks\_completed} \times 3)$$
  $$\text{minimum floor} = 1\text{ leaf}$$
- **Single-Processing Guarantee:** Prevents duplicate completions and double-reward exploits.

### 4. Friends & Social Activity (`/api/friends`)
- **Player Tag Search:** Case-insensitive search, strips leading `#`.
- **Canonical Ordering:** Enforces `user_id_1 < user_id_2` to guarantee single bidirectional friendship records.
- **Mutual Auto-Acceptance:** If user B already sent a request to user A, and user A sends a request to user B, the friendship is instantly marked `accepted`.
- **Friend Stories:** Fetches accepted friends with their latest completed session duration and task description.

### 5. Shop, Themes & Inventory (`/api/shop` & `/api/inventory`)
- **Catalog Seeding:** Pre-seeded with System Default, Peach Blossom, Midnight Velvet, and Supporter Passes.
- **Leaf Purchases:** Atomic validation: cannot buy premium items with leaves, cannot repurchase owned items, leaf balance check.
- **Theme Palettes:** 18-color Material 3 color role palettes matching Android client specifications.
- **Auto-Revert on Expiration:** When a supporter subscription expires, any equipped premium theme automatically reverts to System Default.

### 6. User Safety & Moderation (`/api/safety` & `/ws/safety`)
- **Blocking & Unblocking:** Idempotent blocking, bidirectional filtering in friends list and focus room.
- **Safety WebSocket:** `/ws/safety` pushes instant block notifications to clients.
- **Conduct Violation Reports:** Formal reporting with reporter attribution and reason logging.

### 7. RevenueCat Webhook & Sync (`/api/subscriptions`)
- Webhook handler for `INITIAL_PURCHASE`, `RENEWAL`, `RESTORE`, `EXPIRATION`, and `REVOCATION`.
- On-demand sync endpoint for "Restore Purchases".

---

## 🛠 Configuration (Environment Variables)

| Variable | Default | Description |
|---|---|---|
| `ENVIRONMENT` | `development` | `production` disables mock Google tokens and client-asserted supporter status, and refuses to boot with development secrets |
| `DATABASE_URL` | `sqlite:///./cozy_doubling.db` | Database connection string. Production requires a managed database (PostgreSQL); `postgres://` URLs are used with the psycopg 3 driver |
| `SECRET_KEY` | development value | JWT signing key. **Required** in production (random, ≥ 32 chars) |
| `GOOGLE_CLIENT_IDS` | *(empty)* | Comma-separated Google OAuth **web** client ID(s) whose ID tokens are accepted. Required in production |
| `REVENUECAT_WEBHOOK_AUTH` | development value | Authorization header value configured on the RevenueCat webhook. Required in production |
| `REVENUECAT_API_KEY` | *(empty)* | RevenueCat secret REST key (`sk_...`). When set, "Restore Purchases" verifies entitlements server-side |
| `FIREBASE_CREDENTIALS_FILE` | *(empty)* | Path to a Firebase service-account JSON. When unset, friend push notifications are only logged |
| `ROOM_CAPACITY_LIMIT` | `50` | Maximum participants in global focus room |
| `MAX_REWARDED_TASKS_PER_SESSION` | `20` | Completed tasks that earn leaves in one session (anti leaf-farming) |

### Production checklist
- Set `ENVIRONMENT=production` plus every variable marked *required* above — the server will not start otherwise.
- Serve the API only over HTTPS/WSS (TLS-terminating proxy in front of Uvicorn).
- Run a **single** Uvicorn worker: room presence, safety sockets and the login throttle are in-memory. Scaling out needs a shared store (e.g. Redis pub/sub).

### Migrating 1.1.0 (Supabase) accounts — `[AUTH-12]`
`scripts/import_supabase.py` copies the 1.1.0 data from the Supabase database straight into `DATABASE_URL`. It keeps user IDs, which keeps RevenueCat app user IDs and friend links valid. It also keeps:
- emails;
- bcrypt password hashes, for confirmed email accounts only;
- Google `sub` IDs, so 2.0 Google Sign-In finds the same account;
- profiles (tag, leaves, minutes, supporter flag, theme);
- owned catalog items;
- friendships, blocks and reports;
- processed focus sessions.

Deleted and anonymous users, unfinished sessions and FCM tokens are dropped.

Run it on the staging or production host, **never on a development PC**. No dump file is written, and the output shows counts and opaque user IDs only.

```bash
cd backend
export SUPABASE_DB_URL='postgresql://postgres.<project-ref>:<db-password>@<session-pooler-host>:5432/postgres'
export DATABASE_URL='postgresql://...'        # the 2.0 database
python -m scripts.import_supabase --check     # schema check only, reads no user rows
python -m scripts.import_supabase --dry-run   # full import, rolled back, prints the summary
python -m scripts.import_supabase             # commit
```

- `SUPABASE_DB_URL` is in the Supabase dashboard under **Connect → Session pooler**. The direct connection is IPv6-only. Pass it as an environment variable, not as an argument, so it stays out of the shell history.
- Re-runs upsert by primary key and never delete. Do the dry run against staging. On release day, make Supabase read-only, then import into the **empty** production database.
- If `--check` reports a schema mismatch, compare it with a schema-only `supabase db dump`. That dump contains no user rows, so it is safe on a development PC. Then adjust `SOURCE_TABLES` in the script.
