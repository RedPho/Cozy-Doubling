#!/usr/bin/env python3
"""
Cozy Doubling — Full-Stack Multi-Device Android + Backend E2E Test Runner.

Orchestrates:
1. Isolated live FastAPI/Uvicorn backend with an ephemeral SQLite DB (logged to file).
2. Test-only Coordinator HTTP server (E2eCoordinator) for /kv, /barrier, and /abort.
3. Detection or parallel boot of 1, 2, or 3 real Android emulators:
   - Emulator 1 (port 5554): Medium_Phone_API_37.0 (Wi-Fi enabled)
   - Emulator 2 (port 5556): Medium_Phone_API_37.0_2 (Wi-Fi disabled, cellular data enabled)
   - Emulator 3 (port 5558): Medium_Phone_API_37.0 -read-only (Wi-Fi disabled, cellular data enabled)
4. Single Gradle build (assembleDebug + assembleDebugAndroidTest) with -PapiBaseUrl=http://127.0.0.1:<port>
   followed by parallel `adb install -r -t` across all active emulators.
5. Execution of single-device, 2-device concurrent pairs, and 3-device concurrent triples.
6. Summary report and clean teardown of emulators, coordinator, backend, and temp files.
"""

import argparse
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
BACKEND_DIR = ROOT_DIR / "backend"
ANDROID_DIR = ROOT_DIR / "android"
IS_WINDOWS = platform.system() == "Windows"

TEST_RUNNER = "com.grepho.cozydoubling.test/androidx.test.runner.AndroidJUnitRunner"
APP_PKG = "com.grepho.cozydoubling"

SINGLE_DEVICE_CLASSES = [
    "com.grepho.cozydoubling.e2e.single.AuthAndSettingsE2eTest",
    "com.grepho.cozydoubling.e2e.single.FocusRoomAndSummaryE2eTest",
    "com.grepho.cozydoubling.e2e.single.ShopInventoryJourneyE2eTest",
    "com.grepho.cozydoubling.e2e.single.FriendsSingleDeviceEdgeCasesE2eTest",
]

TWO_DEVICE_SCENARIOS = [
    (
        "twoDevices_mutualPresence_liveTaskSync_andRapidReentry",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFocusRoomE2eTest#twoDevices_mutualPresence_liveTaskSync_andRapidReentry_deviceA",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFocusRoomE2eTest#twoDevices_mutualPresence_liveTaskSync_andRapidReentry_deviceB",
    ),
    (
        "twoDevices_sameAccountSimultaneousRoom_noSelfDuplicate_andLeafSync",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFocusRoomE2eTest#twoDevices_sameAccountSimultaneousRoom_noSelfDuplicate_andLeafSync_deviceA",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFocusRoomE2eTest#twoDevices_sameAccountSimultaneousRoom_noSelfDuplicate_andLeafSync_deviceB",
    ),
    (
        "twoDevices_displayNameChangeReflectedOnReentry",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFocusRoomE2eTest#twoDevices_displayNameChangeReflectedOnReentry_deviceA",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFocusRoomE2eTest#twoDevices_displayNameChangeReflectedOnReentry_deviceB",
    ),
    (
        "twoDevices_friendsFullLifecycle_errors_reject_accept_alreadyFriends_andStory",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_friendsFullLifecycle_errors_reject_accept_alreadyFriends_andStory_deviceA",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_friendsFullLifecycle_errors_reject_accept_alreadyFriends_andStory_deviceB",
    ),
    (
        "twoDevices_mutualRequestAutoAccept",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_mutualRequestAutoAccept_deviceA",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_mutualRequestAutoAccept_deviceB",
    ),
    (
        "twoDevices_safetyReportAndBlock_friendsAndRoom_bidirectionalRealtime",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_safetyReportAndBlock_friendsAndRoom_bidirectionalRealtime_deviceA",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_safetyReportAndBlock_friendsAndRoom_bidirectionalRealtime_deviceB",
    ),
    (
        "twoDevices_accountDeletionCascadesFriendRemoval",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_accountDeletionCascadesFriendRemoval_deviceA",
        "com.grepho.cozydoubling.e2e.twodevice.TwoDeviceFriendsAndSafetyE2eTest#twoDevices_accountDeletionCascadesFriendRemoval_deviceB",
    ),
]

THREE_DEVICE_SCENARIOS = [
    (
        "threeDevices_simultaneousRoomPresence_liveTaskUpdates_andSelectiveLeave",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_simultaneousRoomPresence_liveTaskUpdates_andSelectiveLeave_deviceA",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_simultaneousRoomPresence_liveTaskUpdates_andSelectiveLeave_deviceB",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_simultaneousRoomPresence_liveTaskUpdates_andSelectiveLeave_deviceC",
    ),
    (
        "threeDevices_asymmetricBlockIsolation_roomAndFriends",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_asymmetricBlockIsolation_roomAndFriends_deviceA",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_asymmetricBlockIsolation_roomAndFriends_deviceB",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_asymmetricBlockIsolation_roomAndFriends_deviceC",
    ),
    (
        "threeDevices_multiplePendingRequests_acceptOneRejectOther",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_multiplePendingRequests_acceptOneRejectOther_deviceA",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_multiplePendingRequests_acceptOneRejectOther_deviceB",
        "com.grepho.cozydoubling.e2e.threedevice.ThreeDeviceE2eTest#threeDevices_multiplePendingRequests_acceptOneRejectOther_deviceC",
    ),
]


# --- Test-Only Coordinator Server ---

class CoordinatorState:
    def __init__(self):
        self.cond = threading.Condition()
        self.kv: dict[tuple[str, str], str] = {}
        self.barriers: dict[tuple[str, str], int] = {}
        self.aborted_runs: set[str] = set()

    def abort_run(self, run_id: str) -> None:
        with self.cond:
            self.aborted_runs.add(run_id)
            self.cond.notify_all()

    def put_kv(self, run_id: str, key: str, value: str) -> None:
        with self.cond:
            self.kv[(run_id, key)] = value
            self.cond.notify_all()

    def get_kv(self, run_id: str, key: str, timeout: float) -> tuple[int, str]:
        deadline = time.time() + timeout
        with self.cond:
            while True:
                if run_id in self.aborted_runs:
                    return 410, "aborted"
                if (run_id, key) in self.kv:
                    return 200, self.kv[(run_id, key)]
                remaining = deadline - time.time()
                if remaining <= 0:
                    return 408, f"timeout waiting for key '{key}'"
                self.cond.wait(timeout=remaining)

    def arrive_barrier(self, run_id: str, name: str, count: int, timeout: float) -> tuple[int, str]:
        deadline = time.time() + timeout
        bkey = (run_id, name)
        with self.cond:
            if run_id in self.aborted_runs:
                return 410, "aborted"
            self.barriers[bkey] = self.barriers.get(bkey, 0) + 1
            self.cond.notify_all()
            while True:
                if run_id in self.aborted_runs:
                    return 410, "aborted"
                if self.barriers.get(bkey, 0) >= count:
                    return 200, "ok"
                remaining = deadline - time.time()
                if remaining <= 0:
                    return 408, f"timeout at barrier '{name}' ({self.barriers.get(bkey, 0)}/{count})"
                self.cond.wait(timeout=remaining)


def make_coord_handler(state: CoordinatorState):
    class CoordHandler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):
            pass

        def _send(self, code: int, body: str):
            raw = body.encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            parts = [p for p in parsed.path.split("/") if p]
            qs = urllib.parse.parse_qs(parsed.query)
            timeout = float(qs.get("timeout", ["60"])[0])

            if len(parts) == 3 and parts[0] == "kv":
                code, msg = state.get_kv(parts[1], parts[2], timeout)
                self._send(code, msg)
            else:
                self._send(404, "not found")

        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            parts = [p for p in parsed.path.split("/") if p]
            qs = urllib.parse.parse_qs(parsed.query)
            timeout = float(qs.get("timeout", ["60"])[0])
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length).decode("utf-8") if length > 0 else ""

            if len(parts) == 3 and parts[0] == "kv":
                state.put_kv(parts[1], parts[2], body)
                self._send(200, "ok")
            elif len(parts) == 4 and parts[0] == "barrier":
                code, msg = state.arrive_barrier(parts[1], parts[2], int(parts[3]), timeout)
                self._send(code, msg)
            elif len(parts) == 2 and parts[0] == "abort":
                state.abort_run(parts[1])
                self._send(200, "aborted")
            else:
                self._send(404, "not found")

    return CoordHandler


# --- Environment & ADB Helpers ---

def find_sdk_dir() -> Path | None:
    candidates = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        str(Path.home() / "AppData" / "Local" / "Android" / "Sdk") if IS_WINDOWS else str(Path.home() / "Android" / "Sdk"),
        str(Path.home() / "Library" / "Android" / "sdk"),
    ]
    for c in candidates:
        if c and Path(c).exists():
            return Path(c)
    return None


def find_binary(name: str, sdk_subpath: str | None = None) -> str:
    found = shutil.which(name)
    if found:
        return found
    sdk = find_sdk_dir()
    if sdk and sdk_subpath:
        exe = f"{name}.exe" if IS_WINDOWS else name
        candidate = sdk / sdk_subpath / exe
        if candidate.exists():
            return str(candidate)
    raise FileNotFoundError(f"Could not locate '{name}'. Ensure Android SDK tools are installed.")


def find_java_home() -> str | None:
    if os.environ.get("JAVA_HOME") and Path(os.environ["JAVA_HOME"]).exists():
        return os.environ["JAVA_HOME"]
    if IS_WINDOWS:
        studio_jbr = Path(r"C:\Program Files\Android\Android Studio\jbr")
        if studio_jbr.exists():
            return str(studio_jbr)
    return None


def run_cmd(cmd: list[str], cwd: Path | None = None, env: dict | None = None, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=cwd, env=env, text=True, capture_output=True, check=check)


def list_online_devices(adb: str) -> list[str]:
    proc = run_cmd([adb, "devices"])
    devices = []
    for line in proc.stdout.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return sorted(devices)


def wait_for_backend_health(port: int, server_proc: subprocess.Popen, timeout_s: int = 30) -> None:
    url = f"http://127.0.0.1:{port}/health"
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if server_proc.poll() is not None:
            raise RuntimeError(f"Backend process exited early with code {server_proc.returncode}")
        try:
            with urllib.request.urlopen(url, timeout=2) as resp:
                if resp.status == 200:
                    print(f"[E2E] Backend healthy at {url}")
                    return
        except Exception:
            time.sleep(0.4)
    raise TimeoutError(f"Backend failed health check at {url} within {timeout_s}s")


def wait_for_emulator_serial_ready(adb: str, serial: str, timeout_s: int = 240) -> None:
    print(f"[E2E] Waiting for {serial} to finish booting...")
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        devices = list_online_devices(adb)
        if serial in devices:
            boot_prop = run_cmd([adb, "-s", serial, "shell", "getprop", "sys.boot_completed"], check=False).stdout.strip()
            pm_ready = run_cmd([adb, "-s", serial, "shell", "pm", "path", "android"], check=False).stdout.strip()
            if boot_prop == "1" and pm_ready.startswith("package:"):
                print(f"[E2E] Device ready: {serial}")
                return
        time.sleep(2)
    raise TimeoutError(f"Emulator {serial} did not finish booting within {timeout_s}s")


def configure_device_network_and_settings(adb: str, serial: str, slot_idx: int, backend_port: int, coord_port: int) -> None:
    # Reverse both backend and coordinator ports so 127.0.0.1 works inside every emulator
    run_cmd([adb, "-s", serial, "reverse", f"tcp:{backend_port}", f"tcp:{backend_port}"])
    run_cmd([adb, "-s", serial, "reverse", f"tcp:{coord_port}", f"tcp:{coord_port}"])

    # Animation scale 0 + unlock keyguard + stay awake
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        run_cmd([adb, "-s", serial, "shell", "settings", "put", "global", setting, "0"], check=False)
    run_cmd([adb, "-s", serial, "shell", "input", "keyevent", "82"], check=False)
    run_cmd([adb, "-s", serial, "shell", "svc", "power", "stayon", "true"], check=False)

    # Emulator 2 & 3 disable Wi-Fi (keeps 10.0.2.16 AndroidWifi conflict off) & use mobile data
    if slot_idx == 0:
        run_cmd([adb, "-s", serial, "shell", "svc", "wifi", "enable"], check=False)
        run_cmd([adb, "-s", serial, "shell", "svc", "data", "enable"], check=False)
    else:
        print(f"[E2E] Disabling Wi-Fi on {serial} (slot {slot_idx + 1}) and enabling cellular data...")
        run_cmd([adb, "-s", serial, "shell", "svc", "wifi", "disable"], check=False)
        run_cmd([adb, "-s", serial, "shell", "svc", "data", "enable"], check=False)

    # Wait until Android reports an active CONNECTED internet network (so ConnectivityObserver is Online)
    deadline = time.time() + 45
    while time.time() < deadline:
        conn_dump = run_cmd([adb, "-s", serial, "shell", "dumpsys", "connectivity"], check=False).stdout
        has_internet = ("CONNECTED/CONNECTED" in conn_dump and "INTERNET" in conn_dump)
        if has_internet:
            print(f"[E2E] Network online verified on {serial}")
            return
        time.sleep(1.5)
    print(f"[E2E] Warning: connectivity verification timed out on {serial}; continuing anyway.")


def set_animation_scales(adb: str, serial: str, scale: str) -> None:
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        run_cmd([adb, "-s", serial, "shell", "settings", "put", "global", setting, scale], check=False)


# --- APK Build, Install & Instrumentation Execution ---

def build_apks(backend_port: int) -> tuple[Path, Path]:
    gradlew = str(ANDROID_DIR / ("gradlew.bat" if IS_WINDOWS else "gradlew"))
    gradle_cmd = [
        gradlew,
        "assembleDebug",
        "assembleDebugAndroidTest",
        f"-PapiBaseUrl=http://127.0.0.1:{backend_port}",
    ]
    gradle_env = os.environ.copy()
    java_home = find_java_home()
    if java_home:
        gradle_env["JAVA_HOME"] = java_home

    print(f"[E2E] Building APKs: {' '.join(gradle_cmd)}")
    proc = subprocess.run(gradle_cmd, cwd=ANDROID_DIR, env=gradle_env)
    if proc.returncode != 0:
        raise RuntimeError(f"Gradle APK build failed with exit code {proc.returncode}")

    app_apk = ANDROID_DIR / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    test_apk = ANDROID_DIR / "app" / "build" / "outputs" / "apk" / "androidTest" / "debug" / "app-debug-androidTest.apk"
    if not app_apk.exists() or not test_apk.exists():
        raise FileNotFoundError(f"Expected APKs missing: {app_apk}, {test_apk}")
    return app_apk, test_apk


def install_apks_on_device(adb: str, serial: str, app_apk: Path, test_apk: Path) -> None:
    print(f"[E2E] Installing APKs onto {serial}...")
    for apk in (app_apk, test_apk):
        proc = run_cmd([adb, "-s", serial, "install", "-r", "-t", str(apk)], check=False)
        if proc.returncode != 0:
            raise RuntimeError(f"Failed installing {apk.name} on {serial}:\n{proc.stdout}\n{proc.stderr}")
    run_cmd([adb, "-s", serial, "shell", "pm", "grant", APP_PKG, "android.permission.POST_NOTIFICATIONS"], check=False)


def parse_instrument_success(output: str) -> bool:
    has_ok = "OK (" in output
    has_failure = (
        "FAILURES!!!" in output
        or "Process crashed" in output
        or "INSTRUMENTATION_FAILED" in output
        or "shortMsg=Process crashed" in output
    )
    return has_ok and not has_failure


def run_instrument_target(
    adb: str,
    serial: str,
    target_flag: str,
    target_value: str,
    coord_port: int,
    run_id: str,
    coord_state: CoordinatorState | None = None,
    abort_on_fail: bool = False,
) -> tuple[bool, float, str]:
    start = time.time()
    cmd = [
        adb, "-s", serial, "shell", "am", "instrument", "-w",
        "-e", target_flag, target_value,
        "-e", "coordUrl", f"http://127.0.0.1:{coord_port}",
        "-e", "runId", run_id,
        TEST_RUNNER,
    ]
    proc = run_cmd(cmd, check=False)
    elapsed = time.time() - start
    combined = (proc.stdout or "") + "\n" + (proc.stderr or "")
    ok = proc.returncode == 0 and parse_instrument_success(combined)
    if not ok and abort_on_fail and coord_state is not None:
        coord_state.abort_run(run_id)
    return ok, elapsed, combined


def run_multi_device_scenario(
    adb: str,
    serials: list[str],
    name: str,
    methods: list[str],
    coord_port: int,
    coord_state: CoordinatorState,
) -> tuple[str, bool, float, list[tuple[str, bool, str]]]:
    run_id = uuid.uuid4().hex[:12]
    print(f"\n[E2E] === Running {len(methods)}-device scenario: {name} (runId={run_id}) ===")
    t0 = time.time()
    results: list[tuple[str, bool, str]] = [("", False, "")] * len(methods)

    with ThreadPoolExecutor(max_workers=len(methods)) as pool:
        future_map = {
            pool.submit(
                run_instrument_target,
                adb,
                serials[i],
                "class",
                methods[i],
                coord_port,
                run_id,
                coord_state,
                True,
            ): i
            for i in range(len(methods))
        }
        for fut in as_completed(future_map):
            idx = future_map[fut]
            ok, _, out = fut.result()
            results[idx] = (serials[idx], ok, out)

    elapsed = time.time() - t0
    all_ok = all(r[1] for r in results)
    status_str = "PASS" if all_ok else "FAIL"
    print(f"[E2E] === {name}: {status_str} ({elapsed:.1f}s) ===")
    if not all_ok:
        for ser, ok, out in results:
            if not ok:
                print(f"--- FAILURE output from {ser} ({name}) ---")
                print("\n".join(out.strip().splitlines()[-45:]))
    return name, all_ok, elapsed, results


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Cozy Doubling multi-emulator Android + Backend E2E tests.")
    parser.add_argument("--port", type=int, default=8010, help="Local port for ephemeral FastAPI backend (default: 8010)")
    parser.add_argument("--coord-port", type=int, default=8011, help="Local port for test coordinator server (default: 8011)")
    parser.add_argument("--mode", choices=["all", "single", "two", "three"], default="all", help="Which E2E tier(s) to run")
    parser.add_argument("--scenario", type=str, default=None, help="Optional substring filter for test class or scenario name")
    parser.add_argument("--keep-emulators", action="store_true", help="Leave newly booted emulators running after tests")
    args = parser.parse_args()

    adb = find_binary("adb", "platform-tools")
    emulator_bin = find_binary("emulator", "emulator")
    avds = [a.strip() for a in run_cmd([emulator_bin, "-list-avds"]).stdout.splitlines() if a.strip()]

    needed_devices = {"single": 1, "two": 2, "three": 3, "all": 3}[args.mode]

    temp_dir = Path(tempfile.mkdtemp(prefix="cozy_e2e_"))
    db_path = temp_dir / "cozy_e2e.db"
    db_url = f"sqlite:///{db_path.as_posix()}"
    backend_log_path = temp_dir / "backend.log"
    backend_log_file = None
    server_proc: subprocess.Popen | None = None
    coord_server: ThreadingHTTPServer | None = None
    booted_serials: list[str] = []
    active_serials: list[str] = []
    exit_code = 1

    try:
        # 1. Start isolated live FastAPI/Uvicorn backend (logged to file to avoid Windows pipe deadlock)
        backend_env = os.environ.copy()
        backend_env["DATABASE_URL"] = db_url
        backend_env["PYTHONUNBUFFERED"] = "1"
        server_cmd = [
            sys.executable, "-m", "uvicorn", "app.main:app",
            "--host", "127.0.0.1",
            "--port", str(args.port),
            "--ws-ping-interval", "25",
            "--ws-ping-timeout", "25",
        ]
        print(f"[E2E] Starting isolated backend on 127.0.0.1:{args.port} (DB: {db_path})...")
        backend_log_file = open(backend_log_path, "w", encoding="utf-8")
        server_proc = subprocess.Popen(
            server_cmd,
            cwd=BACKEND_DIR,
            env=backend_env,
            stdout=backend_log_file,
            stderr=subprocess.STDOUT,
            text=True,
        )
        wait_for_backend_health(args.port, server_proc)

        # 2. Start test-only Coordinator HTTP server
        coord_state = CoordinatorState()
        coord_server = ThreadingHTTPServer(("127.0.0.1", args.coord_port), make_coord_handler(coord_state))
        coord_thread = threading.Thread(target=coord_server.serve_forever, daemon=True)
        coord_thread.start()
        print(f"[E2E] Coordinator server listening on 127.0.0.1:{args.coord_port}")

        # 3. Build APKs ONCE while inspecting connected emulators
        app_apk, test_apk = build_apks(args.port)

        existing = list_online_devices(adb)
        active_serials = list(existing[:needed_devices])

        # Slot definitions matching user's installed AVDs (-read-only whenever AVD is shared across slots)
        avd_primary = avds[0] if avds else "Medium_Phone_API_37.0"
        avd_secondary = avds[1] if len(avds) > 1 else avd_primary
        primary_read_only = ["-read-only"] if needed_devices >= 3 else []
        slot_configs = [
            (5554, [emulator_bin, "-avd", avd_primary, *primary_read_only, "-port", "5554", "-no-snapshot-load", "-no-audio", "-no-boot-anim"]),
            (5556, [emulator_bin, "-avd", avd_secondary, "-port", "5556", "-no-snapshot-load", "-no-audio", "-no-boot-anim"]),
            (5558, [emulator_bin, "-avd", avd_primary, "-read-only", "-port", "5558", "-no-snapshot-load", "-no-audio", "-no-boot-anim"]),
        ]

        to_wait: list[str] = []
        used_ports = {int(s.split("-")[1]) for s in active_serials if s.startswith("emulator-") and s.split("-")[1].isdigit()}
        slot_cursor = 0
        while len(active_serials) < needed_devices and slot_cursor < len(slot_configs):
            port_num, emu_cmd = slot_configs[slot_cursor]
            slot_cursor += 1
            if port_num in used_ports:
                continue
            target_serial = f"emulator-{port_num}"
            print(f"[E2E] Booting {target_serial}: {' '.join(emu_cmd)}")
            subprocess.Popen(emu_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            active_serials.append(target_serial)
            booted_serials.append(target_serial)
            to_wait.append(target_serial)

        if to_wait:
            with ThreadPoolExecutor(max_workers=len(to_wait)) as pool:
                list(pool.map(lambda ser: wait_for_emulator_serial_ready(adb, ser), to_wait))

        # 4. Configure networking (Wi-Fi disabled on slots >= 1), adb reverse & install APKs in parallel
        with ThreadPoolExecutor(max_workers=len(active_serials)) as pool:
            futs = [
                pool.submit(
                    configure_device_network_and_settings,
                    adb,
                    active_serials[i],
                    i,
                    args.port,
                    args.coord_port,
                )
                for i in range(len(active_serials))
            ]
            for f in futs:
                f.result()

        with ThreadPoolExecutor(max_workers=len(active_serials)) as pool:
            futs = [pool.submit(install_apks_on_device, adb, ser, app_apk, test_apk) for ser in active_serials]
            for f in futs:
                f.result()

        # 5. Execute requested tiers
        summary: list[tuple[str, str, bool, float]] = []

        if args.mode in ("all", "single"):
            for cls in SINGLE_DEVICE_CLASSES:
                short_name = cls.rsplit(".", 1)[-1]
                if args.scenario and args.scenario.lower() not in short_name.lower() and args.scenario.lower() not in cls.lower():
                    continue
                print(f"\n[E2E] === Running single-device suite: {short_name} on {active_serials[0]} ===")
                ok, elapsed, out = run_instrument_target(
                    adb,
                    active_serials[0],
                    "class",
                    cls,
                    args.coord_port,
                    uuid.uuid4().hex[:12],
                )
                summary.append(("1-Device", short_name, ok, elapsed))
                print(f"[E2E] === {short_name}: {'PASS' if ok else 'FAIL'} ({elapsed:.1f}s) ===")
                if not ok:
                    print(f"--- FAILURE output from {short_name} ---")
                    print("\n".join(out.strip().splitlines()[-45:]))

        if args.mode in ("all", "two"):
            for name, m_a, m_b in TWO_DEVICE_SCENARIOS:
                if args.scenario and args.scenario.lower() not in name.lower():
                    continue
                _, ok, elapsed, _ = run_multi_device_scenario(
                    adb,
                    active_serials[:2],
                    name,
                    [m_a, m_b],
                    args.coord_port,
                    coord_state,
                )
                summary.append(("2-Device", name, ok, elapsed))

        if args.mode in ("all", "three"):
            for name, m_a, m_b, m_c in THREE_DEVICE_SCENARIOS:
                if args.scenario and args.scenario.lower() not in name.lower():
                    continue
                _, ok, elapsed, _ = run_multi_device_scenario(
                    adb,
                    active_serials[:3],
                    name,
                    [m_a, m_b, m_c],
                    args.coord_port,
                    coord_state,
                )
                summary.append(("3-Device", name, ok, elapsed))

        # 6. Summary table
        print("\n==================== E2E SUMMARY ====================")
        passed_count = 0
        for tier, label, ok, elapsed in summary:
            tag = "PASS" if ok else "FAIL"
            if ok:
                passed_count += 1
            print(f"[{tag}] ({tier}) {label} ({elapsed:.1f}s)")
        print(f"Total: {passed_count}/{len(summary)} suites/scenarios passed.")
        print("=====================================================")

        exit_code = 0 if summary and passed_count == len(summary) else 1
        return exit_code

    finally:
        for ser in active_serials:
            set_animation_scales(adb, ser, "1")
            run_cmd([adb, "-s", ser, "shell", "svc", "wifi", "enable"], check=False)
            run_cmd([adb, "-s", ser, "shell", "svc", "data", "enable"], check=False)
            run_cmd([adb, "-s", ser, "shell", "svc", "power", "stayon", "false"], check=False)
            run_cmd([adb, "-s", ser, "reverse", "--remove", f"tcp:{args.port}"], check=False)
            run_cmd([adb, "-s", ser, "reverse", "--remove", f"tcp:{args.coord_port}"], check=False)
        if booted_serials and not args.keep_emulators:
            for ser in booted_serials:
                print(f"[E2E] Shutting down emulator {ser}...")
                run_cmd([adb, "-s", ser, "emu", "kill"], check=False)
        if coord_server:
            coord_server.shutdown()
            coord_server.server_close()
        if server_proc:
            print("[E2E] Stopping isolated backend...")
            server_proc.terminate()
            try:
                server_proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server_proc.kill()
        if backend_log_file:
            backend_log_file.close()
        if exit_code != 0 and backend_log_path.exists():
            print("[E2E] --- Last 80 lines of isolated backend log ---")
            tail = backend_log_path.read_text(encoding="utf-8", errors="replace").splitlines()[-80:]
            print("\n".join(tail))
        shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
