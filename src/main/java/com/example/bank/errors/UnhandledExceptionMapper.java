package com.example.bank.errors;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = LoggerFactory.getLogger(UnhandledExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        LOG.error("Unhandled request failure", exception);
        return ErrorResponses.response(500, ErrorResponses.INTERNAL);
    }
}
