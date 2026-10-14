package com.aibi.platform.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public final class ResourceDocumentDeserializer extends StdDeserializer<JsonNode> implements ContextualDeserializer {
    private final JsonResourceDocument.Kind kind;

    public ResourceDocumentDeserializer() {
        this(null);
    }

    private ResourceDocumentDeserializer(JsonResourceDocument.Kind kind) {
        super(JsonNode.class);
        this.kind = kind;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
            throws JsonMappingException {
        var annotation = property == null ? null : property.getAnnotation(JsonResourceDocument.class);
        if (annotation == null) {
            return context.reportBadDefinition(JsonNode.class, "资源文档缺少显式契约");
        }
        return new ResourceDocumentDeserializer(annotation.value());
    }

    @Override
    public JsonNode deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode source = parser.readValueAsTree();
        try {
            return ResourceDocuments.normalize(source, kind);
        } catch (IllegalArgumentException exception) {
            throw JsonMappingException.from(parser, "资源文档 ID 不合法", exception);
        }
    }
}
