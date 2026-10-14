package com.aibi.platform.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public final class ResourceDocumentSerializer extends StdSerializer<JsonNode> implements ContextualSerializer {
    private final JsonResourceDocument.Kind kind;

    public ResourceDocumentSerializer() {
        this(null);
    }

    private ResourceDocumentSerializer(JsonResourceDocument.Kind kind) {
        super(JsonNode.class);
        this.kind = kind;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
            throws JsonMappingException {
        var annotation = property == null ? null : property.getAnnotation(JsonResourceDocument.class);
        if (annotation == null) {
            return provider.reportBadDefinition(JsonNode.class, "资源文档缺少显式契约");
        }
        return new ResourceDocumentSerializer(annotation.value());
    }

    @Override
    public void serialize(JsonNode value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        try {
            provider.defaultSerializeValue(ResourceDocuments.normalize(value, kind), generator);
        } catch (IllegalArgumentException exception) {
            throw JsonMappingException.from(generator, "资源文档 ID 不合法", exception);
        }
    }
}
