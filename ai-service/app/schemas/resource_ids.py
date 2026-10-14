from __future__ import annotations

import re
from typing import Annotated, Any

from pydantic import BeforeValidator, PlainSerializer, WithJsonSchema


def parse_resource_id(value: Any) -> int:
    """Keep exact Python integers internally and reject all coercion through floats."""
    if type(value) is int:
        identifier = value
    elif isinstance(value, str) and re.fullmatch(r"[1-9][0-9]{0,18}", value):
        identifier = int(value)
    else:
        raise ValueError("资源 ID 必须是规范正整数字符串或整数 token")
    if not 0 < identifier <= 2**63 - 1:
        raise ValueError("资源 ID 超出正有符号 64 位范围")
    return identifier


ResourceId = Annotated[
    int,
    BeforeValidator(parse_resource_id),
    PlainSerializer(str, return_type=str, when_used="json"),
    WithJsonSchema({"type": "string", "pattern": "[1-9][0-9]{0,18}", "examples": ["9007199254740993"]}),
]
