from __future__ import annotations

import hashlib
import hmac
import threading
import time
from collections.abc import Mapping

from app.core.errors import AdmissionRejected


class ReplayGuard:
    def __init__(self, ttl_seconds: int = 120) -> None:
        self._ttl_seconds = ttl_seconds
        self._seen: dict[str, float] = {}
        self._lock = threading.Lock()

    def claim(self, nonce: str, now: float) -> None:
        with self._lock:
            expired = [key for key, deadline in self._seen.items() if deadline <= now]
            for key in expired:
                self._seen.pop(key, None)
            if nonce in self._seen:
                raise AdmissionRejected("DELEGATION_REPLAYED", "内部委托请求已被使用", 401)
            self._seen[nonce] = now + self._ttl_seconds


class DelegationSigner:
    def __init__(self, secret: str, max_clock_skew_seconds: int = 30) -> None:
        self._secret = secret.encode("utf-8")
        self._max_clock_skew_seconds = max_clock_skew_seconds

    def sign(self, method: str, path: str, body: bytes, timestamp: int, nonce: str) -> str:
        canonical = self._canonical(method, path, body, timestamp, nonce)
        return hmac.new(self._secret, canonical, hashlib.sha256).hexdigest()

    def verify(
        self,
        method: str,
        path: str,
        body: bytes,
        headers: Mapping[str, str],
        replay_guard: ReplayGuard,
        now: float | None = None,
    ) -> None:
        current = time.time() if now is None else now
        try:
            timestamp = int(headers["x-ai-timestamp"])
            nonce = headers["x-ai-nonce"]
            supplied = headers["x-ai-signature"]
        except (KeyError, ValueError) as exc:
            raise AdmissionRejected("DELEGATION_MISSING", "内部委托签名头不完整", 401) from exc
        if abs(current - timestamp) > self._max_clock_skew_seconds:
            raise AdmissionRejected("DELEGATION_EXPIRED", "内部委托签名已过期", 401)
        expected = self.sign(method, path, body, timestamp, nonce)
        # 非 ASCII 签名也必须以认证失败关闭，不能让 compare_digest 抛出 500。
        if not supplied.isascii() or not hmac.compare_digest(expected, supplied):
            raise AdmissionRejected("DELEGATION_INVALID", "内部委托签名无效", 401)
        replay_guard.claim(nonce, current)

    @staticmethod
    def _canonical(method: str, path: str, body: bytes, timestamp: int, nonce: str) -> bytes:
        digest = hashlib.sha256(body).hexdigest()
        return f"{method.upper()}\n{path}\n{timestamp}\n{nonce}\n{digest}".encode("utf-8")

