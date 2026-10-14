from __future__ import annotations


class AiEngineError(RuntimeError):
    def __init__(self, code: str, message: str, status_code: int = 400) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


class AdmissionRejected(AiEngineError):
    pass


class TaskCancelled(AiEngineError):
    def __init__(self) -> None:
        super().__init__("TASK_CANCELLED", "任务已取消", 409)


class TaskTimedOut(AiEngineError):
    def __init__(self) -> None:
        super().__init__("TASK_TIMEOUT", "任务已超过执行时限", 408)


class ModelFailure(AiEngineError):
    def __init__(self, message: str = "模型服务暂时不可用") -> None:
        super().__init__("MODEL_UNAVAILABLE", message, 503)

