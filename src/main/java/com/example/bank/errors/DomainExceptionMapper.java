package com.example.bank.errors;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

public final class DomainExceptionMapper implements ExceptionMapper<DomainException> {
    @Override
    public Response toResponse(DomainException exception) {
        return ErrorResponses.response(exception.status(), exception.error());
    }
}
