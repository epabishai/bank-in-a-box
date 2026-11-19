package com.example.bank.errors;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

final class ErrorResponses {
    static final ApiError VALIDATION = new ApiError("Request validation failed.", "VALIDATION_ERROR");
    static final ApiError INTERNAL = new ApiError("An internal error occurred.", "INTERNAL_ERROR");
    static final ApiError NOT_FOUND = new ApiError("Route not found.", "NOT_FOUND");
    static final ApiError HTTP_ERROR = new ApiError("Request could not be processed.", "HTTP_ERROR");

    private ErrorResponses() {
    }

    static Response response(int status, ApiError error) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(error).build();
    }

    static ApiError forStatus(int status) {
        return status >= 500 ? INTERNAL
                : status == 404 ? NOT_FOUND
                : status == 400 ? VALIDATION : HTTP_ERROR;
    }
}
