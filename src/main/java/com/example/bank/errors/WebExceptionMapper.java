package com.example.bank.errors;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

public final class WebExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        int status = original.getStatus();
        // Preserve protocol headers such as Allow, but never a framework-provided entity.
        return Response.fromResponse(original).header(HttpHeaders.CONTENT_LENGTH, null)
                .header(HttpHeaders.CONTENT_ENCODING, null)
                .entity(ErrorResponses.forStatus(status)).type(MediaType.APPLICATION_JSON_TYPE).build();
    }
}
