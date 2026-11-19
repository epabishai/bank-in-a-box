package com.example.bank.errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

public final class JsonExceptionMapper implements ExceptionMapper<JsonProcessingException> {
    @Override
    public Response toResponse(JsonProcessingException exception) {
        // Request parsing failures are classified by JsonRequestInterceptor.
        // A processor can be null even when serialization fails, so fail closed here.
        return ErrorResponses.response(500, ErrorResponses.INTERNAL);
    }
}
