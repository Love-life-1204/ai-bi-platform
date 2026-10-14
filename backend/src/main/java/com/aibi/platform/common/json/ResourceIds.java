package com.aibi.platform.common.json;

import com.fasterxml.jackson.databind.JsonNode;

/** Canonical, positive signed BIGINT resource identifiers. */
public final class ResourceIds {
    private ResourceIds() {
    }

    public static long parse(String token) {
        if (token == null || !token.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("资源 ID 必须是规范正整数");
        }
        return Long.parseLong(token);
    }

    public static long parse(JsonNode node) {
        if (node == null || (!node.isTextual() && !node.isIntegralNumber())) {
            throw new IllegalArgumentException("资源 ID 必须是字符串或整数 token");
        }
        return parse(node.asText());
    }

    public static boolean valid(JsonNode node) {
        try {
            parse(node);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
