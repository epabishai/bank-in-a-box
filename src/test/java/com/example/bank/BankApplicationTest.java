package com.example.bank;

import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankApplicationTest {
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.16-alpine");

    static {
        POSTGRES.start();
    }

    @RegisterExtension
    static final DropwizardAppExtension<BankConfiguration> APP =
            new DropwizardAppExtension<>(
                    BankApplication.class,
                    ResourceHelpers.resourceFilePath("config.yml"),
                    ConfigOverride.config("server.applicationConnectors[0].port", "0"),
                    ConfigOverride.config("server.adminConnectors[0].port", "0"),
                    ConfigOverride.config("database.url", POSTGRES.getJdbcUrl()),
                    ConfigOverride.config("database.user", POSTGRES.getUsername()),
                    ConfigOverride.config("database.password", POSTGRES.getPassword())
            );

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

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

    @Test
    void appliesSchemaMigrationAtStartup() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet migrations = statement.executeQuery(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE success")) {
            assertTrue(migrations.next());
            assertEquals(1, migrations.getInt(1));
        }

        assertIndexContains("accounts", "lower(email)");
        assertIndexContains("transactions", "account_id, created_at DESC");

        UUID accountId = UUID.randomUUID();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO accounts (id, name, email) VALUES ('" + accountId
                            + "', 'Test account', 'test@example.com')");
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO accounts (id, name, email) VALUES ('" + UUID.randomUUID()
                            + "', 'Duplicate', 'TEST@example.com')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO accounts (id, name, email, balance_cents) VALUES ('"
                            + UUID.randomUUID() + "', 'Negative', 'negative@example.com', -1)"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO transactions (id, account_id, type, amount_cents) VALUES ('"
                            + UUID.randomUUID() + "', '" + accountId + "', 'DEPOSIT', 0)"));
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void assertIndexContains(String table, String expectedDefinition) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery(
                     "SELECT indexdef FROM pg_indexes WHERE tablename = '" + table + "'")) {
            boolean found = false;
            while (indexes.next()) {
                if (indexes.getString(1).contains(expectedDefinition)) {
                    found = true;
                }
            }
            assertTrue(found, "Expected index definition containing " + expectedDefinition);
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
