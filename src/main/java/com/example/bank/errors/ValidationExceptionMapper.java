package com.example.bank.errors;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

public final class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        // Invalid resource return values are server defects, not invalid requests.
        boolean invalidReturn = exception.getConstraintViolations().stream()
                .anyMatch(violation -> {
                    for (var node : violation.getPropertyPath()) {
                        if (node.getKind() == ElementKind.RETURN_VALUE) {
                            return true;
                        }
                    }
                    return false;
                });
        return invalidReturn
                ? ErrorResponses.response(500, ErrorResponses.INTERNAL)
                : ErrorResponses.response(400, ErrorResponses.VALIDATION);
    }
}
