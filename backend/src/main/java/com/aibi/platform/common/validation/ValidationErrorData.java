package com.aibi.platform.common.validation;

import java.util.List;

public record ValidationErrorData(List<FieldViolation> errors) {

    public ValidationErrorData {
        errors = List.copyOf(errors);
    }
}
