package com.example.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
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
    void createsAndGetsAccountAndReturnsExpectedErrors() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String email = "account-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> created = post(client, APP.getLocalPort(), "/api/accounts",
                "{\"name\":\"Ada Lovelace\",\"email\":\"" + email + "\",\"initial_deposit\":125.50}");

        assertEquals(201, created.statusCode());
        JsonNode account = mapper.readTree(created.body());
        UUID accountId = UUID.fromString(account.get("id").asText());
        assertEquals("Ada Lovelace", account.get("name").asText());
        assertEquals(email, account.get("email").asText());
        assertEquals(0, account.get("balance").decimalValue().compareTo(new BigDecimal("125.50")));

        HttpResponse<String> fetched = get(client, APP.getLocalPort(), "/api/accounts/" + accountId);
        assertEquals(200, fetched.statusCode());
        assertEquals(accountId.toString(), mapper.readTree(fetched.body()).get("id").asText());

        HttpResponse<String> missing = get(client, APP.getLocalPort(), "/api/accounts/" + UUID.randomUUID());
        assertEquals(404, missing.statusCode());
        assertEquals("ACCOUNT_NOT_FOUND", mapper.readTree(missing.body()).get("code").asText());

        HttpResponse<String> malformed = get(client, APP.getLocalPort(), "/api/accounts/not-a-uuid");
        assertEquals(400, malformed.statusCode());
        assertEquals("VALIDATION_ERROR", mapper.readTree(malformed.body()).get("code").asText());

        HttpResponse<String> duplicate = post(client, APP.getLocalPort(), "/api/accounts",
                "{\"name\":\"Duplicate\",\"email\":\"" + email.toUpperCase(Locale.ROOT) + "\"}");
        assertEquals(409, duplicate.statusCode());
        assertEquals("EMAIL_ALREADY_EXISTS", mapper.readTree(duplicate.body()).get("code").asText());

        HttpResponse<String> invalid = post(client, APP.getLocalPort(), "/api/accounts",
                "{\"name\":\"\",\"email\":\"not-an-email\",\"initial_deposit\":1}");
        assertEquals(400, invalid.statusCode());
        assertEquals("VALIDATION_ERROR", mapper.readTree(invalid.body()).get("code").asText());
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
                    "INSERT INTO accounts (id, name, email, balance_cents) VALUES ('" + accountId
                            + "', 'Test account', 'test@example.com', 0)");
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO accounts (id, name, email) VALUES ('" + UUID.randomUUID()
                            + "', 'Duplicate', 'TEST@example.com')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO accounts (id, name, email, balance_cents) VALUES ('"
                            + UUID.randomUUID() + "', 'Negative', 'negative@example.com', -1)"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO transactions (id, account_id, type, amount_cents) VALUES ('"
                            + UUID.randomUUID() + "', '" + accountId + "', 'DEPOSIT', 0)"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO transactions (id, account_id, type, amount_cents) VALUES ('"
                            + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', 'DEPOSIT', 1)"));
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

    private static HttpResponse<String> post(HttpClient client, int port, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
