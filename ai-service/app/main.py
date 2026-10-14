from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import health_router, router
from app.core.config import Settings
from app.core.errors import AiEngineError
from app.persistence.checkpointer import CheckpointerHandle
from app.persistence.task_store import TaskStore
from app.providers.base import RuleBasedLanguageProvider
from app.security.delegation import DelegationSigner, ReplayGuard
from app.services.task_manager import TaskManager
from app.tools.java_client import JavaToolClient


def create_app(settings: Settings | None = None) -> FastAPI:
    configuration = settings or Settings.load()
    checkpointer_handle = CheckpointerHandle(
        configuration.checkpoint_backend,
        configuration.checkpoint_sqlite_path,
        configuration.checkpoint_redis_url,
    )

    @asynccontextmanager
    async def lifespan(application: FastAPI):
        store = TaskStore(configuration.task_db_path)
        checkpointer = checkpointer_handle.__enter__()
        tools = JavaToolClient(
            configuration.java_base_url,
            configuration.shared_secret,
            store,
            configuration.node_timeout_seconds,
        )
        signer = DelegationSigner(configuration.shared_secret)
        replay_guard = ReplayGuard()

        async def verify_delegation(request: Request) -> None:
            body = await request.body()
            signer.verify(request.method, request.url.path, body, request.headers, replay_guard)

        application.state.task_store = store
        application.state.verify_delegation = verify_delegation
        manager = TaskManager(
            store,
            checkpointer,
            tools,
            RuleBasedLanguageProvider(),
            configuration.max_tool_calls,
            configuration.max_workers,
        )
        application.state.task_manager = manager
        manager.recover()
        try:
            yield
        finally:
            manager.close()
            checkpointer_handle.__exit__(None, None, None)

    application = FastAPI(
        title="AI BI 智能分析引擎",
        version="0.1.0",
        docs_url=None if configuration.environment == "production" else "/docs",
        lifespan=lifespan,
    )
    application.include_router(router)
    application.include_router(health_router)

    @application.exception_handler(AiEngineError)
    async def handle_ai_error(_: Request, error: AiEngineError) -> JSONResponse:
        return JSONResponse(
            status_code=error.status_code,
            content={"code": error.code, "message": error.message, "data": None},
        )

    return application


app = create_app()

