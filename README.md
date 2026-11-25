# 🌿 Cozy Doubling

**A quiet space to focus together, unhurried and calm.**

Cozy Doubling is a calm body-doubling and focus companion consisting of a native Android application (Clean Architecture + Jetpack Compose) and an independent FastAPI Python backend service.

---

## 📁 Repository Structure

```text
Cozy-Doubling/
├── android/            # Native Android application (Jetpack Compose, Kotlin)
│   ├── app/            # Clean Architecture layers: core/, features/, data/, ui/
│   ├── gradle/         # Gradle wrapper & version catalog
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── backend/            # Independent FastAPI Python backend service
│   ├── app/            # FastAPI routers, SQLAlchemy models, schemas, services, WebSockets
│   ├── tests/          # Backend test modules
│   ├── requirements.txt
│   ├── run.py          # Backend entry point
│   └── run_tests.py    # Automated backend test runner
├── docs/               # Privacy Policy, Terms of Service, Play Store listing copy
├── specs/              # Product backlog, epics, and resolved bug tracking
└── README.md
```

---

## 🚀 Getting Started

### 1. Python Backend Service

The backend runs on Python 3.10+ using FastAPI, SQLAlchemy, and Uvicorn.

```bash
cd backend

# (Optional) Create and activate a virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\Activate.ps1

# Install dependencies
pip install -r requirements.txt

# Run the backend server (starts at http://127.0.0.1:8000)
python run.py

# Run all automated backend tests
python -X faulthandler -u run_tests.py
```

- **Interactive API Documentation (Swagger):** [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- **Database:** Defaults to `backend/cozy_doubling.db` (SQLite, configurable via `DATABASE_URL`).

### 2. Android Application

The Android app is built with Jetpack Compose, Material 3, Kotlin Coroutines/Flow, and strict Clean Architecture boundaries (`ArchitectureBoundaryTest`).

1. Open **Android Studio** and open the `Cozy-Doubling/android` folder.
2. Allow Gradle to sync.
3. Run or debug on an emulator or physical device.

#### Connecting Android App to a Local Backend
- By default, debug builds connect to `http://10.0.2.2:8000` (configured in `android/app/build.gradle.kts`). Override with `-PapiBaseUrl=http://YOUR_HOST:8000` if needed.
- On a physical device over USB:
  ```bash
  adb reverse tcp:8000 tcp:8000
  ```

#### Running Android Tests & Debug Build
```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

---

## ✨ Implemented Highlights

- **Live Cozy Room (`/ws/room`)**: Work quietly alongside real-time participants, broadcast your active task & progress, and stay in sync with server-verified session time and connection status.
- **Background Focus Continuity**: Foreground Service (`FocusSessionService`) with persistent chronometer notification quick actions (`Complete Task`, `Finish`), optional Picture-in-Picture mini-widget, and unexpected app-closure Summary recovery.
- **Fair Leaf Economy & Oasis**: Earn verified Leaves from focused minutes and completed tasks (up to 12 hours per sitting with 10-minute reconnect grace and 30-minute abandonment wrap-up), unlock Material 3 workspace themes, or equip Supporter Pass themes.
- **Friends & Real-Time Updates (`/ws/friends`)**: Add focus buddies via unique 8-character Connect Codes, get instant incoming request/acceptance updates, remove friends cleanly, and receive optional room-entry notifications.
- **Safety & Privacy (`/ws/safety`)**: Instant bidirectional blocking (across Focus Rooms, Friends, and notifications), Settings **Blocked Users** unblock management, abuse reporting, encrypted JWT storage (`EncryptedSharedPreferences`), and one-tap permanent account deletion.
- **UI Guardrails**: Font-scale bounds (`0.85x–1.25x`) in `CozyDoublingTheme` and matching client/server input length limits (`TextLimits`).

---

## 🛠 Tech Stack

- **Client (`android/`)**:
  - **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
  - **Architecture:** Clean Architecture (`core/` contracts, `features/` MVVM, `data/` implementations enforced by `ArchitectureBoundaryTest`)
  - **Networking & Serialization:** OkHttp3 (REST + WebSockets) & `kotlinx.serialization`
  - **Storage & Security:** AndroidX Security `EncryptedSharedPreferences` + Kotlin Coroutines/Flow
  - **Push & Payments:** Firebase Cloud Messaging & Google Play Billing / RevenueCat
- **Server (`backend/`)**:
  - **Framework:** [FastAPI](https://fastapi.tiangolo.com/) + Uvicorn
  - **Database:** SQLAlchemy ORM (SQLite / PostgreSQL)
  - **Realtime:** Native WebSockets (`/ws/room`, `/ws/friends`, `/ws/safety`)
  - **Auth:** Google Sign-In verification + Email/Password (Bcrypt) + JWT

---

## 📄 License

This project is licensed under a **Source Available License**.

You are free to view, download, and learn from the source code for personal and educational use. Commercial use, redistribution, or cloning of the app for public release is strictly prohibited.

See the [LICENSE](LICENSE) file for full details.
