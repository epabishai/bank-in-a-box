package com.example.bank.errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;

/** Classifies Jackson failures while reading a request, independently of its processor metadata. */
public final class JsonRequestInterceptor implements ReaderInterceptor {
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        try {
            return context.proceed();
        } catch (InvalidDefinitionException exception) {
            // A broken Jackson configuration is a server defect, not bad input.
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Invalid JSON request", exception);
        }
    }
}
