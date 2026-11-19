package com.example.bank.errors;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExceptionMapperTest {
    @Test
    void mapsEachDomainException() {
        var mapper = new DomainExceptionMapper();
        assertResponse(mapper.toResponse(new AccountNotFoundException()), 404, "ACCOUNT_NOT_FOUND");
        assertResponse(mapper.toResponse(new EmailAlreadyExistsException()), 409, "EMAIL_ALREADY_EXISTS");
        assertResponse(mapper.toResponse(new InsufficientFundsException()), 422, "INSUFFICIENT_FUNDS");
    }

    @Test
    void mapsConstraintViolations() {
        assertResponse(new ValidationExceptionMapper().toResponse(new ConstraintViolationException(Set.of())),
                400, "VALIDATION_ERROR");
    }

    @Test
    void mapsUnclassifiedJacksonFailuresAsServerErrors() throws Exception {
        var mapper = new JsonExceptionMapper();
        try (JsonParser parser = new JsonFactory().createParser("{}");
             var generator = new JsonFactory().createGenerator(new StringWriter())) {
            assertResponse(mapper.toResponse(JsonMappingException.from(parser, "secret")), 500, "INTERNAL_ERROR");
            assertResponse(mapper.toResponse(JsonMappingException.from(generator, "secret")), 500, "INTERNAL_ERROR");
            assertResponse(mapper.toResponse(new JsonMappingException("secret")), 500, "INTERNAL_ERROR");
        }
    }

    @Test
    void mapsWebExceptionsWithoutLeakingEntityAndPreservesHeaders() {
        var source = Response.status(405).header("Allow", "GET").entity("secret").build();
        Response response = new WebExceptionMapper().toResponse(new WebApplicationException(source));
        assertResponse(response, 405, "HTTP_ERROR");
        assertEquals("GET", response.getHeaderString("Allow"));
    }

    @Test
    void mapsUnhandledErrorsWithoutLeakingDetails() {
        Response response = new UnhandledExceptionMapper().toResponse(new RuntimeException("secret"));
        assertResponse(response, 500, "INTERNAL_ERROR");
        assertEquals("An internal error occurred.", ((ApiError) response.getEntity()).error());
    }

    private static void assertResponse(Response response, int status, String code) {
        assertEquals(status, response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        assertEquals(code, ((ApiError) response.getEntity()).code());
    }
}
