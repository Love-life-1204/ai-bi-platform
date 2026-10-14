from __future__ import annotations

import pytest

from app.core.errors import AdmissionRejected
from app.security.delegation import DelegationSigner, ReplayGuard


def test_signed_request_is_accepted_once() -> None:
    signer = DelegationSigner("a" * 32)
    body = b'{"safe":true}'
    headers = {
        "x-ai-timestamp": "1000",
        "x-ai-nonce": "nonce-1234567890",
        "x-ai-signature": signer.sign("POST", "/internal/v1/ai/tasks/execute", body, 1000, "nonce-1234567890"),
    }
    guard = ReplayGuard()
    signer.verify("POST", "/internal/v1/ai/tasks/execute", body, headers, guard, now=1001)
    with pytest.raises(AdmissionRejected, match="已被使用"):
        signer.verify("POST", "/internal/v1/ai/tasks/execute", body, headers, guard, now=1002)


def test_body_tampering_and_expired_signature_are_rejected() -> None:
    signer = DelegationSigner("b" * 32)
    signature = signer.sign("POST", "/tool", b"{}", 1000, "nonce-a")
    headers = {"x-ai-timestamp": "1000", "x-ai-nonce": "nonce-a", "x-ai-signature": signature}
    with pytest.raises(AdmissionRejected):
        signer.verify("POST", "/tool", b'{"tenant_id":999}', headers, ReplayGuard(), now=1001)
    with pytest.raises(AdmissionRejected, match="过期"):
        signer.verify("POST", "/tool", b"{}", headers, ReplayGuard(), now=1100)

