#!/usr/bin/env python3
"""
Cozy Doubling — Full-Stack Android + Backend E2E Test Runner.

Orchestrates:
1. Isolated live FastAPI/Uvicorn backend with an ephemeral SQLite DB.
2. Android emulator detection or automatic boot from installed AVDs.
3. `adb reverse tcp:<port> tcp:<port>` and animation scale disablement.
4. `gradlew connectedDebugAndroidTest -PapiBaseUrl=http://127.0.0.1:<port>`.
5. Deterministic teardown of server, port reverse, animations, and temp DB.
"""

import argparse
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
BACKEND_DIR = ROOT_DIR / "backend"
ANDROID_DIR = ROOT_DIR / "android"
IS_WINDOWS = platform.system() == "Windows"


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
    return devices


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
            time.sleep(0.5)
    raise TimeoutError(f"Backend failed health check at {url} within {timeout_s}s")


def wait_for_emulator_boot(adb: str, timeout_s: int = 180) -> str:
    print("[E2E] Waiting for Android emulator to finish booting...")
    deadline = time.time() + timeout_s
    serial = None
    while time.time() < deadline:
        devices = list_online_devices(adb)
        if devices:
            serial = devices[0]
            boot_prop = run_cmd([adb, "-s", serial, "shell", "getprop", "sys.boot_completed"], check=False).stdout.strip()
            pm_ready = run_cmd([adb, "-s", serial, "shell", "pm", "path", "android"], check=False).stdout.strip()
            if boot_prop == "1" and pm_ready.startswith("package:"):
                print(f"[E2E] Device ready: {serial}")
                return serial
        time.sleep(2)
    raise TimeoutError(f"Android device did not finish booting within {timeout_s}s")


def set_animation_scales(adb: str, serial: str, scale: str) -> None:
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        run_cmd([adb, "-s", serial, "shell", "settings", "put", "global", setting, scale], check=False)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Cozy Doubling full-stack Android + Backend E2E tests.")
    parser.add_argument("--port", type=int, default=8000, help="Local port for ephemeral FastAPI backend (default: 8000)")
    parser.add_argument("--avd", type=str, default=None, help="AVD name to boot if no device is connected")
    parser.add_argument("--test-class", type=str, default=None, help="Optional fully qualified E2E test class name")
    parser.add_argument("--keep-emulator", action="store_true", help="Keep newly booted emulator running after tests")
    args = parser.parse_args()

    adb = find_binary("adb", "platform-tools")
    temp_dir = Path(tempfile.mkdtemp(prefix="cozy_e2e_"))
    db_path = temp_dir / "cozy_e2e.db"
    db_url = f"sqlite:///{db_path.as_posix()}"

    server_proc: subprocess.Popen | None = None
    backend_log_file = None
    backend_log_path = temp_dir / "backend.log"
    emulator_proc: subprocess.Popen | None = None
    booted_emulator = False
    serial: str | None = None
    exit_code = 1

    try:
        # 1. Start isolated live FastAPI/Uvicorn backend
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

        # 2. Ensure Android emulator/device is attached & booted
        devices = list_online_devices(adb)
        if devices:
            serial = devices[0]
            print(f"[E2E] Using already connected device: {serial}")
        else:
            emulator_bin = find_binary("emulator", "emulator")
            avds = [a.strip() for a in run_cmd([emulator_bin, "-list-avds"]).stdout.splitlines() if a.strip()]
            if not avds:
                raise RuntimeError("No connected Android device and no AVDs found via 'emulator -list-avds'.")
            chosen_avd = args.avd if args.avd else avds[0]
            if chosen_avd not in avds:
                raise RuntimeError(f"AVD '{chosen_avd}' not found. Available AVDs: {avds}")

            print(f"[E2E] Booting AVD '{chosen_avd}'...")
            emulator_proc = subprocess.Popen(
                [emulator_bin, "-avd", chosen_avd, "-no-snapshot-load", "-no-audio", "-no-boot-anim"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            booted_emulator = True
            serial = wait_for_emulator_boot(adb)

        # 3. Configure adb reverse & disable animations for deterministic Compose tests
        run_cmd([adb, "-s", serial, "reverse", f"tcp:{args.port}", f"tcp:{args.port}"])
        set_animation_scales(adb, serial, "0")
        # Unlock keyguard & keep screen awake
        run_cmd([adb, "-s", serial, "shell", "input", "keyevent", "82"], check=False)
        run_cmd([adb, "-s", serial, "shell", "svc", "power", "stayon", "true"], check=False)

        # 4. Run Gradle instrumentation tests targeting the live local backend
        gradlew = str(ANDROID_DIR / ("gradlew.bat" if IS_WINDOWS else "gradlew"))
        gradle_cmd = [
            gradlew,
            "connectedDebugAndroidTest",
            f"-PapiBaseUrl=http://127.0.0.1:{args.port}",
        ]
        if args.test_class:
            gradle_cmd.append(f"-Pandroid.testInstrumentationRunnerArguments.class={args.test_class}")
        else:
            gradle_cmd.append("-Pandroid.testInstrumentationRunnerArguments.package=com.grepho.cozydoubling.e2e")

        gradle_env = os.environ.copy()
        gradle_env["ANDROID_SERIAL"] = serial
        java_home = find_java_home()
        if java_home:
            gradle_env["JAVA_HOME"] = java_home

        print(f"[E2E] Running: {' '.join(gradle_cmd)}")
        result = subprocess.run(gradle_cmd, cwd=ANDROID_DIR, env=gradle_env)
        exit_code = result.returncode
        return exit_code

    finally:
        if serial:
            set_animation_scales(adb, serial, "1")
            run_cmd([adb, "-s", serial, "shell", "svc", "power", "stayon", "false"], check=False)
            run_cmd([adb, "-s", serial, "reverse", "--remove", f"tcp:{args.port}"], check=False)
            if booted_emulator and not args.keep_emulator:
                print(f"[E2E] Shutting down emulator {serial}...")
                run_cmd([adb, "-s", serial, "emu", "kill"], check=False)
        if emulator_proc and booted_emulator and not args.keep_emulator:
            try:
                emulator_proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator_proc.terminate()
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
            print("[E2E] --- Last 60 lines of isolated backend log ---")
            tail = backend_log_path.read_text(encoding="utf-8", errors="replace").splitlines()[-60:]
            print("\n".join(tail))
        shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
