# 🌿 Cozy Doubling

**A quiet space to focus together, unhurried and calm.**

Cozy Doubling is a cross-platform body doubling and focus ecosystem consisting of a native Android application and an independent FastAPI Python backend service.

---

## 📁 Repository Structure

```
Cozy-Doubling/
├── android/            # Native Android application (Jetpack Compose, Kotlin)
│   ├── app/            # Main application module
│   ├── gradle/         # Gradle wrapper files
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── ...
├── backend/            # Independent FastAPI Python backend service
│   ├── app/            # FastAPI app, routers, models, schemas, websockets
│   ├── tests/          # Pytest backend test suite
│   ├── requirements.txt
│   ├── run.py          # Backend entry point
│   └── run_tests.py    # Test runner script
├── docs/               # Privacy policy, Terms of Service, screenshots
├── specs/              # Product specifications and backlog epics
└── README.md
```

---

## 🚀 Getting Started

### 1. Python Backend Service

The backend runs on Python 3.10+ using FastAPI, SQLAlchemy, and Uvicorn.

```bash
# Navigate to the backend directory
cd backend

# (Optional) Create and activate a virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\Activate.ps1

# Install dependencies
pip install -r requirements.txt

# Run the backend server (starts at http://127.0.0.1:8000)
python run.py
# Or with uvicorn directly:
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Run backend tests
python run_tests.py
```

- **Interactive API Documentation (Swagger)**: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- **Database**: Defaults to `backend/cozy_doubling.db` (SQLite).

### 2. Android Application

The Android app is built with Jetpack Compose, Material 3, and Kotlin Coroutines.

1. Open **Android Studio**.
2. Select **Open** and choose the `android` folder (`Cozy-Doubling/android`).
3. Allow Gradle to sync.
4. Run or debug on an emulator or physical device.

#### Connecting Android App to the Local Backend:
- Android Emulator connects to `http://10.0.2.2:8000` (pre-configured in `ApiClient.kt`).
- If using a physical device over USB, forward the port:
  ```bash
  adb reverse tcp:8000 tcp:8000
  ```

#### Running Android Tests:
```bash
cd android
./gradlew testDebugUnitTest
```

#### Running Full Android + Backend E2E Tests:
See [docs/e2e_testing_guide.md](docs/e2e_testing_guide.md) and [specs/02_e2e_test_tickets.md](specs/02_e2e_test_tickets.md).
```bash
python scripts/run_e2e.py
```

---

## ✨ Features

- **Focus Rooms**: Join or create quiet spaces for deep work with real-time WebSocket presence.
- **Economy & Rewards**: Progress through your focus journey and unlock cozy themes.
- **Real-Time Sync**: Synchronized room state and live updates across participants.
- **Safety & Moderation**: User blocking and reporting with immediate WebSocket disconnect.
- **Privacy First**: Secure authentication, password hashing, and clean account management.

---

## 🛠 Tech Stack

- **Client (`android/`)**:
  - UI: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
  - Networking: OkHttp3 & Retrofit (REST + WebSockets)
  - Architecture: MVVM with Kotlin Coroutines & Flow
  - Payments: RevenueCat & Google Play Billing
- **Server (`backend/`)**:
  - Framework: [FastAPI](https://fastapi.tiangolo.com/) (Python 3.14 compatible)
  - Database: SQLAlchemy ORM (SQLite / PostgreSQL)
  - Realtime: Native WebSockets (Focus Room Presence & Safety events)
  - Auth: OAuth2 Password Bearer + JWT + Bcrypt

---

## 📄 License

This project is licensed under a **Source Available License**. 

You are free to view, download, and learn from the source code for personal and educational use. Commercial use, redistribution, or cloning of the app for public release is strictly prohibited. 

See the [LICENSE](LICENSE) file for full details.
