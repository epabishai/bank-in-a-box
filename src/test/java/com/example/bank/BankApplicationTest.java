package com.example.bank;

import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankApplicationTest {
    @RegisterExtension
    static final DropwizardAppExtension<BankConfiguration> APP =
            new DropwizardAppExtension<>(
                    BankApplication.class,
                    ResourceHelpers.resourceFilePath("config.yml"),
                    ConfigOverride.config("server.applicationConnectors[0].port", "0"),
                    ConfigOverride.config("server.adminConnectors[0].port", "0")
            );

    @Test
    void servesHealthEndpoints() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> apiHealth = get(client, APP.getLocalPort(), "/api/health");
        HttpResponse<String> root = get(client, APP.getLocalPort(), "/");
        HttpResponse<String> adminHealth = get(client, APP.getAdminPort(), "/healthcheck");

        assertEquals(200, apiHealth.statusCode());
        assertTrue(apiHealth.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        assertTrue(apiHealth.body().contains("\"status\":\"ok\""));
        assertEquals(404, root.statusCode());
        assertEquals(200, adminHealth.statusCode());
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
