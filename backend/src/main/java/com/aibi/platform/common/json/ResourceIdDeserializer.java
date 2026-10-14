package com.aibi.platform.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Parses the original integer token without conversion through a floating point number. */
public final class ResourceIdDeserializer extends StdDeserializer<Long> implements ContextualDeserializer {
    private final boolean nullable;

    public ResourceIdDeserializer() {
        this(false);
    }

    private ResourceIdDeserializer(boolean nullable) {
        super(Long.class);
        this.nullable = nullable;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        return new ResourceIdDeserializer(property != null && property.getType().hasRawClass(Long.class));
    }

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING) && !parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        }
        String token = parser.getText();
        if (!token.matches("[1-9][0-9]{0,18}")) {
            return (Long) context.handleWeirdStringValue(Long.class, token, "资源 ID 必须是规范正整数");
        }
        try {
            return Long.valueOf(token);
        } catch (NumberFormatException exception) {
            return (Long) context.handleWeirdStringValue(Long.class, token, "资源 ID 超出有符号 64 位范围");
        }
    }

    @Override
    public Long getNullValue(DeserializationContext context) throws JsonMappingException {
        if (nullable) {
            return null;
        }
        return context.reportInputMismatch(Long.class, "必填资源 ID 或 ID 集合元素不能为 null");
    }
}
