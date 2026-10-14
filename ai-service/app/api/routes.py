from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Query, Request, status

from app.schemas.models import TaskAccepted, TaskEvent, TaskExecuteRequest, TaskResumeRequest, TaskStatus
from app.services.task_manager import TaskManager


def task_manager(request: Request) -> TaskManager:
    return request.app.state.task_manager


router = APIRouter(prefix="/internal/v1/ai/tasks", tags=["internal-ai"])


@router.post("/execute", response_model=TaskAccepted, status_code=status.HTTP_202_ACCEPTED)
async def execute(
    request: Request,
    payload: TaskExecuteRequest,
    manager: Annotated[TaskManager, Depends(task_manager)],
) -> TaskAccepted:
    await request.app.state.verify_delegation(request)
    return manager.submit(payload)


@router.post("/{task_id}/resume", response_model=TaskAccepted, status_code=status.HTTP_202_ACCEPTED)
async def resume(
    task_id: str,
    request: Request,
    payload: TaskResumeRequest,
    manager: Annotated[TaskManager, Depends(task_manager)],
) -> TaskAccepted:
    await request.app.state.verify_delegation(request)
    return manager.resume(task_id, payload)


@router.post("/{task_id}/cancel", response_model=TaskStatus)
async def cancel(
    task_id: str,
    request: Request,
    manager: Annotated[TaskManager, Depends(task_manager)],
) -> TaskStatus:
    await request.app.state.verify_delegation(request)
    return manager.cancel(task_id)


@router.get("/{task_id}", response_model=TaskStatus)
async def get_status(
    task_id: str,
    request: Request,
    manager: Annotated[TaskManager, Depends(task_manager)],
) -> TaskStatus:
    await request.app.state.verify_delegation(request)
    return manager.required_status(task_id)


@router.get("/{task_id}/events", response_model=list[TaskEvent])
async def events(
    task_id: str,
    request: Request,
    manager: Annotated[TaskManager, Depends(task_manager)],
    after: int = Query(default=0, ge=0),
    limit: int = Query(default=100, ge=1, le=500),
) -> list[TaskEvent]:
    await request.app.state.verify_delegation(request)
    manager.required_status(task_id)
    return request.app.state.task_store.events(task_id, after=after, limit=limit)


health_router = APIRouter(tags=["health"])


@health_router.get("/health/live")
def live() -> dict[str, str]:
    return {"status": "UP"}


@health_router.get("/health/ready")
def ready(request: Request) -> dict[str, str]:
    request.app.state.task_store.events("readiness-probe", 0, 1)
    return {"status": "UP"}

