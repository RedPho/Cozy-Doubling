"""Brute-force protection for email/password sign-in ([AUTH-02]).

In-memory and per-process: good enough for a single backend instance. When the backend is
scaled horizontally this must move to a shared store (e.g. Redis) together with the room
and safety WebSocket managers.
"""
import threading
import time
from collections import defaultdict, deque


class LoginThrottle:
    def __init__(self, max_failures: int = 5, window_seconds: int = 15 * 60, lockout_seconds: int = 15 * 60):
        self.max_failures = max_failures
        self.window_seconds = window_seconds
        self.lockout_seconds = lockout_seconds
        self._failures: dict[str, deque] = defaultdict(deque)
        self._locked_until: dict[str, float] = {}
        self._lock = threading.Lock()

    def retry_after_seconds(self, key: str, now: float | None = None) -> int:
        now = time.monotonic() if now is None else now
        with self._lock:
            until = self._locked_until.get(key)
            if until is None:
                return 0
            if until <= now:
                self._locked_until.pop(key, None)
                self._failures.pop(key, None)
                return 0
            return int(until - now) + 1

    def record_failure(self, key: str, now: float | None = None) -> None:
        now = time.monotonic() if now is None else now
        with self._lock:
            attempts = self._failures[key]
            attempts.append(now)
            while attempts and attempts[0] < now - self.window_seconds:
                attempts.popleft()
            if len(attempts) >= self.max_failures:
                self._locked_until[key] = now + self.lockout_seconds
                attempts.clear()

    def reset(self, key: str) -> None:
        with self._lock:
            self._failures.pop(key, None)
            self._locked_until.pop(key, None)

    def clear(self) -> None:
        with self._lock:
            self._failures.clear()
            self._locked_until.clear()


login_throttle = LoginThrottle()
