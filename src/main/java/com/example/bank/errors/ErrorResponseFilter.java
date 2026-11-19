package com.example.bank.errors;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/** Jersey may bypass exception mappers when a WebApplicationException already has an entity. */
public final class ErrorResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        int status = response.getStatus();
        if (status < 400 || response.getEntity() instanceof ApiError) {
            return;
        }
        response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        response.getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
        response.setEntity(ErrorResponses.forStatus(status), null, MediaType.APPLICATION_JSON_TYPE);
    }
}
