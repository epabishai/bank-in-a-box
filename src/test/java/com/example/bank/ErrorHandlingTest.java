package com.example.bank;

import com.example.bank.errors.AccountNotFoundException;
import com.example.bank.errors.EmailAlreadyExistsException;
import com.example.bank.errors.InsufficientFundsException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlingTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.16-alpine");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    static {
        POSTGRES.start();
    }

    @RegisterExtension
    static final DropwizardAppExtension<BankConfiguration> APP = new DropwizardAppExtension<>(
            TestApplication.class, ResourceHelpers.resourceFilePath("config.yml"),
            ConfigOverride.config("server.applicationConnectors[0].port", "0"),
            ConfigOverride.config("server.adminConnectors[0].port", "0"),
            // Verify that application wiring overrides Dropwizard defaults.
            ConfigOverride.config("server.registerDefaultExceptionMappers", "true"),
            ConfigOverride.config("database.url", POSTGRES.getJdbcUrl()),
            ConfigOverride.config("database.user", POSTGRES.getUsername()),
            ConfigOverride.config("database.password", POSTGRES.getPassword()));

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    public static class TestApplication extends BankApplication {
        @Override
        public void run(BankConfiguration config, Environment environment) {
            super.run(config, environment);
            environment.jersey().register(new TestResource());
            environment.servlets().addServlet("test-failure", new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    response.sendError(503, "secret servlet detail");
                }
            }).addMapping("/servlet-failure");
        }
    }

    @Path("/error-test")
    @Produces(MediaType.APPLICATION_JSON)
    public static class TestResource {
        public record Input(@NotBlank String name, @NotNull Integer number) {}

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        public Input validate(@NotNull @Valid Input input) {
            return input;
        }

        @GET @Path("/missing")
        public void missing() { throw new AccountNotFoundException(); }

        @GET @Path("/duplicate")
        public void duplicate() { throw new EmailAlreadyExistsException(); }

        @GET @Path("/funds")
        public void funds() { throw new InsufficientFundsException(); }

        @GET @Path("/failure")
        public void failure() { throw new IllegalStateException("secret stack detail"); }

        @GET @Path("/framework")
        public void framework() {
            throw new WebApplicationException(Response.status(503).entity("secret backend details").build());
        }

        @GET @Path("/invalid-return") @NotNull
        public String invalidReturn() { return null; }

        @GET @Path("/serialization-failure")
        public BrokenOutput serializationFailure() { return new BrokenOutput(); }

        @GET @Path("/null-processor-failure")
        public BrokenMappingOutput nullProcessorFailure() { return new BrokenMappingOutput(); }

        @GET @Path("/mapping-failure")
        public String mappingFailure() throws JsonMappingException {
            throw new JsonMappingException("secret resource detail");
        }

        @POST @Path("/null-processor-input")
        @Consumes(MediaType.APPLICATION_JSON)
        public String nullProcessorInput(BrokenInput input) { return "unexpected"; }

        @POST @Path("/bad-definition")
        @Consumes(MediaType.APPLICATION_JSON)
        public String badDefinition(BadDefinitionInput input) { return "unexpected"; }

        @JsonDeserialize(using = BrokenInputDeserializer.class)
        public static class BrokenInput {}

        @JsonDeserialize(using = BadDefinitionDeserializer.class)
        public static class BadDefinitionInput {}

        public static class BrokenInputDeserializer extends JsonDeserializer<BrokenInput> {
            @Override
            public BrokenInput deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                throw new JsonMappingException("secret input detail");
            }
        }

        public static class BadDefinitionDeserializer extends JsonDeserializer<BadDefinitionInput> {
            @Override
            public BadDefinitionInput deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                throw InvalidDefinitionException.from(parser, "secret configuration detail", (JavaType) null);
            }
        }

        public static class BrokenOutput {
            public String getValue() { throw new IllegalStateException("secret accessor detail"); }
        }

        public static class BrokenMappingOutput {
            public String getValue() throws JsonMappingException {
                throw new JsonMappingException("secret mapping detail");
            }
        }
    }

    @Test
    void mapsDomainFailures() throws Exception {
        assertError(request("GET", "/api/error-test/missing", null, null, null), 404, "ACCOUNT_NOT_FOUND");
        assertError(request("GET", "/api/error-test/duplicate", null, null, null), 409, "EMAIL_ALREADY_EXISTS");
        assertError(request("GET", "/api/error-test/funds", null, null, null), 422, "INSUFFICIENT_FUNDS");
    }

    @Test
    void mapsValidationAndJsonFailures() throws Exception {
        HttpResponse<String> success = request("POST", "/api/error-test",
                "{\"name\":\"ok\",\"number\":1}", "application/json", null);
        assertEquals(200, success.statusCode(), success.body());
        assertEquals("ok", JSON.readTree(success.body()).path("name").asText());
        assertError(request("POST", "/api/error-test", "{\"name\":\"\",\"number\":1}", "application/json", null),
                400, "VALIDATION_ERROR");
        assertError(request("POST", "/api/error-test", "{\"name\":", "application/json", null),
                400, "VALIDATION_ERROR");
        assertError(request("POST", "/api/error-test", "{\"name\":\"ok\",\"number\":{}}", "application/json", null),
                400, "VALIDATION_ERROR");
        assertError(request("POST", "/api/error-test/null-processor-input", "{}", "application/json", null),
                400, "VALIDATION_ERROR");
        assertError(request("POST", "/api/error-test/bad-definition", "{}", "application/json", null),
                500, "INTERNAL_ERROR");
    }

    @Test
    void hidesUnexpectedFailureDetailsAndHandlesInvalidReturnValues() throws Exception {
        assertError(request("GET", "/api/error-test/failure", null, null, null), 500, "INTERNAL_ERROR");
        assertError(request("GET", "/api/error-test/framework", null, null, null), 503, "INTERNAL_ERROR");
        assertError(request("GET", "/api/error-test/invalid-return", null, null, null), 500, "INTERNAL_ERROR");
        assertError(request("GET", "/servlet-failure", null, null, null), 503, "INTERNAL_ERROR");
    }

    @Test
    void responseSerializationFailuresAreServerErrors() throws Exception {
        assertError(request("GET", "/api/error-test/serialization-failure", null, null, null), 500, "INTERNAL_ERROR");
        assertError(request("GET", "/api/error-test/null-processor-failure", null, null, null), 500, "INTERNAL_ERROR");
        assertError(request("GET", "/api/error-test/mapping-failure", null, null, null), 500, "INTERNAL_ERROR");
    }

    @Test
    void unknownApiRoutesAndNonGetRequestsAreJson() throws Exception {
        for (String path : new String[]{"/api/unknown", "/api"}) {
            assertError(request("GET", path, null, null, null), 404, "NOT_FOUND");
        }
        assertError(request("POST", "/outside", "{}", "application/json", null), 404, "NOT_FOUND");
    }

    @Test
    void nonApiGetRoutesServeSpa() throws Exception {
        for (String path : new String[]{"/", "/unknown", "/unknown/deep"}) {
            var response = request("GET", path, null, null, null);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
            assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElse(""));
        }
    }

    @Test
    void preservesFrameworkStatusAndAllowHeader() throws Exception {
        HttpResponse<String> method = request("PUT", "/api/error-test", "", "application/json", null);
        assertError(method, 405, "HTTP_ERROR");
        assertTrue(method.headers().firstValue("Allow").orElse("").contains("POST"));
        assertError(request("GET", "/api/health", null, null, "text/plain"), 406, "HTTP_ERROR");
        assertError(request("POST", "/api/error-test", "x", "text/plain", null), 415, "HTTP_ERROR");
        assertError(request("TRACE", "/api/health", null, null, null), 405, "HTTP_ERROR");
    }

    private static HttpResponse<String> request(String method, String path, String body,
                                                String contentType, String accept)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + APP.getLocalPort() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) builder.header("Content-Type", contentType);
        if (accept != null) builder.header("Accept", accept);
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertError(HttpResponse<String> response, int status, String code) throws IOException {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                response.toString());
        JsonNode body = JSON.readTree(response.body());
        assertEquals(2, body.size(), response.body());
        assertEquals(code, body.path("code").asText(), response.body());
        assertFalse(body.path("error").asText().isBlank(), response.body());
        assertFalse(response.body().contains("secret"), response.body());
        assertFalse(response.body().contains("Exception"), response.body());
    }
}
