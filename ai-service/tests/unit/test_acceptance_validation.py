from datetime import UTC, datetime, timedelta

import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.core.errors import AdmissionRejected
from app.schemas.models import DelegatedContext, TimeRange
from app.security.delegation import DelegationSigner, ReplayGuard


@pytest.mark.parametrize("secret", [None, "local-development-secret-change-me"])
def test_production_rejects_missing_or_development_secret(monkeypatch, secret):
    monkeypatch.setenv("AI_ENV", "production")
    monkeypatch.setenv("AI_CHECKPOINT_BACKEND", "redis")
    monkeypatch.setenv("AI_CHECKPOINT_REDIS_URL", "redis://localhost:6379/15")
    if secret is None:
        monkeypatch.delenv("AI_SHARED_SECRET", raising=False)
    else:
        monkeypatch.setenv("AI_SHARED_SECRET", secret)
    with pytest.raises(ValueError, match="AI_SHARED_SECRET"):
        Settings.load()


def test_production_accepts_explicit_non_default_secret(monkeypatch):
    monkeypatch.setenv("AI_ENV", "production")
    monkeypatch.setenv("AI_CHECKPOINT_BACKEND", "redis")
    monkeypatch.setenv("AI_CHECKPOINT_REDIS_URL", "redis://localhost:6379/15")
    monkeypatch.setenv("AI_SHARED_SECRET", "test-only-secret-with-more-than-32-characters")
    assert Settings.load().environment == "production"


def test_non_ascii_signature_is_auth_failure_not_server_error():
    signer = DelegationSigner("test-only-shared-secret")
    with pytest.raises(AdmissionRejected) as caught:
        signer.verify("GET", "/internal/v1/ai/tasks/1", b"", {
            "x-ai-timestamp": "1000", "x-ai-nonce": "nonce-for-test",
            "x-ai-signature": "\u00e9" * 64,
        }, ReplayGuard(), now=1000)
    assert caught.value.status_code == 401


@pytest.mark.parametrize("aware_end", [False, True])
def test_naive_time_range_is_validation_error(aware_end):
    start = datetime(2026, 10, 1)
    end = datetime(2026, 11, 1, tzinfo=UTC if aware_end else None)
    with pytest.raises(ValidationError):
        TimeRange(field_id=1, start=start, end=end)


def test_delegation_rejects_naive_deadline():
    now = datetime.now(UTC)
    with pytest.raises(ValidationError):
        DelegatedContext(issuer="java", audience="ai-bi-engine", jti="n" * 20,
            expires_at=now + timedelta(minutes=2), deadline_at=now.replace(tzinfo=None),
            tenant_id=1, member_id=2, workspace_id=3, user_id=4, session_id=5,
            tenant_permission_version=1, member_permission_version=1, context_version=0,
            request_id="request-qa", task_id="qa-task", conversation_id="qa-conversation",
            thread_id="qa-thread-0000001", trace_id="qa-trace-0001", allowed_tools={"query_execute"})
