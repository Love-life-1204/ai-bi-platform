package com.aibi.platform.common.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicit schemas for the few API documents which contain resource IDs. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@JsonSerialize(using = ResourceDocumentSerializer.class)
@JsonDeserialize(using = ResourceDocumentDeserializer.class)
public @interface JsonResourceDocument {
    Kind value();

    enum Kind {
        METRIC_FORMULA, TIME_SPEC, CHART_SPEC, DASHBOARD_FILTERS
    }
}
