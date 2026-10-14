from __future__ import annotations

import threading
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from typing import Any

from langgraph.types import Command

from app.core.errors import AiEngineError, TaskCancelled, TaskTimedOut
from app.graph.builder import GraphServices, build_graph
from app.persistence.task_store import TERMINAL, TaskStore
from app.providers.base import LanguageProvider
from app.schemas.models import DelegatedContext, TaskAccepted, TaskExecuteRequest, TaskResumeRequest, TaskStatus
from app.tools.java_client import JavaToolClient


class TaskManager:
    def __init__(
        self,
        store: TaskStore,
        checkpointer: Any,
        tools: JavaToolClient,
        provider: LanguageProvider,
        max_tool_calls: int,
        max_workers: int,
    ) -> None:
        self._store = store
        self._executor = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="ai-graph")
        self._locks: dict[str, threading.Lock] = {}
        self._locks_guard = threading.Lock()
        services = GraphServices(tools, store, provider, max_tool_calls, self._progress)
        self._graph = build_graph(checkpointer, services)

    def submit(self, request: TaskExecuteRequest) -> TaskAccepted:
        context = request.context
        created = self._store.create(
            context.task_id,
            context.request_id,
            context.trace_id,
            context.thread_id,
            request.model_dump(mode="json"),
        )
        if created:
            self._store.append_event(context.task_id, context.trace_id, "accepted", {"status": "ACCEPTED"})
            self._executor.submit(self._run_new, request)
        current = self.required_status(context.task_id)
        return TaskAccepted(request_id=context.request_id, task_id=context.task_id, status=current.status)

    def resume(self, task_id: str, request: TaskResumeRequest) -> TaskAccepted:
        status = self.required_status(task_id)
        if status.status != "WAITING_CLARIFICATION":
            raise AiEngineError("TASK_NOT_WAITING", "任务当前不处于待澄清状态", 409)
        original = self._store.request(task_id)
        if original is None:
            raise AiEngineError("TASK_NOT_FOUND", "任务不存在", 404)
        old = DelegatedContext.model_validate(original["context"])
        new = request.context
        if (old.tenant_id, old.member_id, old.workspace_id, old.conversation_id, old.thread_id) != (
            new.tenant_id, new.member_id, new.workspace_id, new.conversation_id, new.thread_id
        ):
            raise AiEngineError("RESUME_CONTEXT_MISMATCH", "恢复上下文与原任务不一致", 403)
        if new.context_version < old.context_version:
            raise AiEngineError("CONTEXT_VERSION_STALE", "会话上下文版本已过期", 409)
        self._store.update(task_id, "ACCEPTED")
        self._store.append_event(task_id, new.trace_id, "accepted", {"status": "RESUMED"})
        self._executor.submit(self._run_resume, task_id, request)
        return TaskAccepted(request_id=new.request_id, task_id=task_id, status="ACCEPTED")

    def cancel(self, task_id: str) -> TaskStatus:
        status = self.required_status(task_id)
        if status.status in TERMINAL:
            return status
        self._store.request_cancel(task_id)
        return self.required_status(task_id)

    def recover(self) -> None:
        """Continue non-terminal work from its LangGraph checkpoint after process restart."""
        for value in self._store.recoverable_requests():
            request = TaskExecuteRequest.model_validate(value)
            self._executor.submit(self._run_recovered, request)

    def close(self) -> None:
        self._executor.shutdown(wait=True, cancel_futures=True)

    def required_status(self, task_id: str) -> TaskStatus:
        status = self._store.status(task_id)
        if status is None:
            raise AiEngineError("TASK_NOT_FOUND", "任务不存在", 404)
        return status

    def _run_new(self, request: TaskExecuteRequest) -> None:
        context = request.context
        input_state = {
            "context": context,
            "question": request.question,
            "dataset_id": request.dataset_id,
            "tool_call_count": 0,
        }
        self._invoke(context, input_state)

    def _run_resume(self, task_id: str, request: TaskResumeRequest) -> None:
        command = Command(resume=request.clarification, update={"context": request.context})
        self._invoke(request.context, command)

    def _run_recovered(self, request: TaskExecuteRequest) -> None:
        context = request.context
        config = {"configurable": {"thread_id": context.thread_id}}
        snapshot = self._graph.get_state(config)
        if getattr(snapshot, "values", None):
            self._invoke(context, None)
        else:
            self._run_new(request)

    def _invoke(self, context: DelegatedContext, graph_input: Any) -> None:
        lock = self._task_lock(context.task_id)
        if not lock.acquire(blocking=False):
            return
        try:
            self._store.update(context.task_id, "RUNNING")
            config = {"configurable": {"thread_id": context.thread_id}}
            output = self._graph.invoke(graph_input, config=config)
            interrupts = output.get("__interrupt__", ()) if isinstance(output, dict) else ()
            if interrupts:
                value = getattr(interrupts[0], "value", interrupts[0])
                clarification = value if isinstance(value, dict) else {"question": str(value)}
                self._store.update(context.task_id, "WAITING_CLARIFICATION", clarification=clarification)
                self._store.append_event(context.task_id, context.trace_id, "clarification", clarification)
                return
            answer = output["answer"].model_dump(mode="json")
            chart = output.get("chart_spec")
            result = {
                "answer": answer,
                "chart_spec": None if chart is None else chart.model_dump(mode="json"),
                "evidence": [{
                    "query_id": output["query_result"].query_id,
                    "trace_id": context.trace_id,
                    "publication": output["query_result"].publication.model_dump(mode="json"),
                    "metric_version_ids": [str(identifier) for identifier in output["validated_plan"].metric_version_ids],
                    "dataset_version_id": str(output["validated_plan"].dataset_version_id),
                    "time_range": None if output["validated_plan"].time_range is None else output["validated_plan"].time_range.model_dump(mode="json"),
                }],
            }
            self._store.update(context.task_id, "SUCCEEDED", result=result)
            self._store.append_event(context.task_id, context.trace_id, "result", result)
            self._store.append_event(context.task_id, context.trace_id, "done", {"status": "SUCCEEDED"})
        except TaskCancelled as exc:
            self._fail(context, "CANCELLED", exc.code, exc.message)
        except TaskTimedOut as exc:
            self._fail(context, "TIMEOUT", exc.code, exc.message)
        except AiEngineError as exc:
            self._fail(context, "FAILED", exc.code, exc.message)
        except Exception:
            self._fail(context, "FAILED", "AI_GRAPH_FAILED", "智能分析流程执行失败")
        finally:
            lock.release()

    def _fail(self, context: DelegatedContext, status: str, code: str, message: str) -> None:
        self._store.update(context.task_id, status, error_code=code)
        self._store.append_event(context.task_id, context.trace_id, "error", {"code": code, "message": message})
        self._store.append_event(context.task_id, context.trace_id, "done", {"status": status})

    def _progress(self, context: DelegatedContext, stage: str, status: str) -> None:
        self._store.append_event(context.task_id, context.trace_id, "progress", {"stage": stage, "status": status})

    def _task_lock(self, task_id: str) -> threading.Lock:
        with self._locks_guard:
            return self._locks.setdefault(task_id, threading.Lock())

