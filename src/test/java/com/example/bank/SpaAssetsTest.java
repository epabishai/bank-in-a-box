package com.example.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpaAssetsTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @RegisterExtension
    static final DropwizardAppExtension<BankConfiguration> APP = new DropwizardAppExtension<>(
            WebApplication.class, ResourceHelpers.resourceFilePath("config.yml"),
            ConfigOverride.config("server.applicationConnectors[0].port", "0"),
            ConfigOverride.config("server.adminConnectors[0].port", "0"),
            ConfigOverride.config("server.registerDefaultExceptionMappers", "true"));

    /** Exercises the production HTTP registration, without starting a database. */
    public static class WebApplication extends BankApplication {
        @Override
        public void run(BankConfiguration configuration, Environment environment) {
            registerWeb(configuration, environment);
        }
    }

    @Test
    void servesIndexAndClientRoutesWithNoCacheAndHeadWithoutBody() throws Exception {
        String index = request("GET", "/").body();
        assertTrue(index.contains("SPA test fixture"));
        for (String path : List.of("/", "/index.html", "/accounts/123", "/unknown/deep?x=1",
                "/missing.js", "/apiary", "/assets", "/assets/")) {
            for (String method : List.of("GET", "HEAD")) {
                var response = request(method, path);
                assertEquals(200, response.statusCode(), path);
                assertTrue(header(response, "Content-Type").startsWith("text/html"), path);
                assertEquals("no-cache", header(response, "Cache-Control"), path);
                assertEquals(method.equals("HEAD") ? "" : index, response.body(), path);
            }
        }
    }

    @Test
    void servesAssetsWithMimeTypesAndOnlyHashedAssetsAreImmutable() throws Exception {
        for (String file : List.of("fixture-Ab12cd34.js", "fixture-Ab12cd34.css")) {
            var response = request("GET", "/assets/" + file);
            assertEquals(200, response.statusCode());
            String mime = header(response, "Content-Type");
            assertTrue(file.endsWith(".js") ? mime.contains("javascript") : mime.startsWith("text/css"), mime);
            assertEquals("public, max-age=31536000, immutable", header(response, "Cache-Control"));
            assertEquals("nosniff", header(response, "X-Content-Type-Options"));
            assertFalse(response.body().isEmpty());
            var head = request("HEAD", "/assets/" + file);
            assertEquals(200, head.statusCode());
            assertEquals(mime, header(head, "Content-Type"));
            assertEquals(header(response, "Cache-Control"), header(head, "Cache-Control"));
            assertEquals("", head.body());
        }
        var plain = request("GET", "/plain.svg");
        assertEquals(200, plain.statusCode());
        assertEquals("image/svg+xml", header(plain, "Content-Type"));
        assertEquals("no-cache", header(plain, "Cache-Control"));
        assertTrue(plain.body().startsWith("<svg"));
    }

    @Test
    void preservesApiRoutingAndJsonErrorsIncludingExactApiPath() throws Exception {
        var health = request("GET", "/api/health");
        assertEquals(200, health.statusCode());
        assertTrue(header(health, "Content-Type").startsWith("application/json"));
        assertEquals("ok", JSON.readTree(health.body()).path("status").asText());
        assertEquals("", header(health, "Cache-Control"));
        for (String path : List.of("/api", "/api/", "/api/unknown", "/api/index.html")) {
            assertJsonError(request("GET", path), 404, "NOT_FOUND");
            var head = request("HEAD", path);
            assertEquals(404, head.statusCode());
            assertTrue(header(head, "Content-Type").startsWith("application/json"));
            assertEquals("", head.body());
        }
        assertJsonError(request("POST", "/api/health"), 405, "HTTP_ERROR");
    }

    @Test
    void onlyGetAndHeadCanServeSpaOrAssets() throws Exception {
        for (String method : List.of("POST", "PUT", "DELETE", "PATCH", "OPTIONS")) {
            for (String path : List.of("/", "/client/route", "/assets/fixture-Ab12cd34.js")) {
                assertJsonError(request(method, path), 404, "NOT_FOUND");
            }
        }
        assertJsonError(request("GET", "/_error"), 404, "NOT_FOUND");
    }

    @Test
    void doesNotAffectAdminContext() throws Exception {
        var health = request(APP.getAdminPort(), "GET", "/healthcheck");
        assertEquals(200, health.statusCode());
        var index = request(APP.getAdminPort(), "GET", "/");
        assertEquals(200, index.statusCode()); // Dropwizard's administrative index.
        assertFalse(index.body().contains("SPA test fixture"));
        assertTrue(index.body().contains("healthcheck"));
        for (String path : List.of("/accounts/123", "/assets/fixture-Ab12cd34.js")) {
            var response = request(APP.getAdminPort(), "GET", path);
            assertEquals(404, response.statusCode(), path);
            assertFalse(response.body().contains("SPA test fixture"));
            assertFalse(header(response, "Cache-Control").contains("immutable"));
        }
    }

    @Test
    void cannotTraverseOrReadClasspathOutsideAssets() throws Exception {
        for (String path : List.of("/../config.yml", "/assets/../../config.yml", "/%2e%2e/config.yml",
                "/assets/%2e%2e/%2e%2e/config.yml", "/%252e%252e/config.yml",
                "/..%2fconfig.yml", "/..%5cconfig.yml", "/assets//fixture-Ab12cd34.js",
                "/assets/./fixture-Ab12cd34.js", "/index.html;ignored", "/%00",
                "/%61pi/unknown")) {
            var response = request("GET", path);
            assertTrue(response.statusCode() == 400 || response.statusCode() == 404,
                    path + ": " + response.statusCode());
            assertFalse(response.body().contains("SPA test fixture"), path);
            assertFalse(response.body().contains("driverClass"), path);
            assertFalse(response.body().contains("safe fixture"), path);
        }
        for (String path : List.of("/config.yml", "/db/migration/V1__init.sql",
                "/com/example/bank/BankApplication.class", "/META-INF/MANIFEST.MF")) {
            var response = request("GET", path);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("SPA test fixture"), path);
            assertEquals("no-cache", header(response, "Cache-Control"));
        }
    }

    private static void assertJsonError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(header(response, "Content-Type").startsWith("application/json"));
        var json = JSON.readTree(response.body());
        assertEquals(2, json.size());
        assertEquals(code, json.path("code").asText());
        assertFalse(json.path("error").asText().isBlank());
        assertFalse(header(response, "Cache-Control").contains("immutable"));
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    private static HttpResponse<String> request(String method, String path) throws Exception {
        return request(APP.getLocalPort(), method, path);
    }

    private static HttpResponse<String> request(int port, String method, String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
