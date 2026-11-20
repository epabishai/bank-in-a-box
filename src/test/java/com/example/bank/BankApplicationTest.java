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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void depositsAndWithdrawalsValidateAmountsAndDoNotWriteOnFailure() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String email = "ledger-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> created = post(client, APP.getLocalPort(), "/api/accounts",
                "{\"name\":\"Ledger account\",\"email\":\"" + email + "\",\"initial_deposit\":10.00}");
        UUID accountId = UUID.fromString(mapper.readTree(created.body()).get("id").asText());

        HttpResponse<String> deposit = post(client, APP.getLocalPort(), "/api/accounts/" + accountId + "/deposit",
                "{\"amount\":2.50,\"note\":\"Paycheck\"}");
        assertEquals(201, deposit.statusCode());
        JsonNode depositResponse = mapper.readTree(deposit.body());
        assertEquals("DEPOSIT", depositResponse.get("type").asText());
        assertEquals("Paycheck", depositResponse.get("note").asText());
        assertEquals(0, depositResponse.get("amount").decimalValue().compareTo(new BigDecimal("2.50")));

        HttpResponse<String> withdrawal = post(
                client, APP.getLocalPort(), "/api/accounts/" + accountId + "/withdraw",
                "{\"amount\":1.25,\"note\":\"Cash\"}");
        assertEquals(201, withdrawal.statusCode());
        assertEquals("WITHDRAWAL", mapper.readTree(withdrawal.body()).get("type").asText());

        for (String amount : List.of("0", "-1", "1.001")) {
            HttpResponse<String> invalid = post(
                    client, APP.getLocalPort(), "/api/accounts/" + accountId + "/withdraw",
                    "{\"amount\":" + amount + "}");
            assertEquals(400, invalid.statusCode());
            assertEquals("VALIDATION_ERROR", mapper.readTree(invalid.body()).get("code").asText());
        }

        HttpResponse<String> overdraft = post(
                client, APP.getLocalPort(), "/api/accounts/" + accountId + "/withdraw",
                "{\"amount\":100.00}");
        assertEquals(422, overdraft.statusCode());
        assertEquals("INSUFFICIENT_FUNDS", mapper.readTree(overdraft.body()).get("code").asText());

        JsonNode account = mapper.readTree(get(client, APP.getLocalPort(), "/api/accounts/" + accountId).body());
        assertEquals(0, account.get("balance").decimalValue().compareTo(new BigDecimal("11.25")));
        assertEquals(1_125, ledgerBalance(accountId));
        assertEquals(3, transactionCount(accountId));
    }

    @Test
    void paginatesTransactionHistoryNewestFirstAndValidatesParameters() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String email = "history-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> created = post(client, APP.getLocalPort(), "/api/accounts",
                "{\"name\":\"History account\",\"email\":\"" + email + "\"}");
        UUID accountId = UUID.fromString(mapper.readTree(created.body()).get("id").asText());
        insertHistoryTransactions(accountId, 23);

        JsonNode firstPage = mapper.readTree(get(client, APP.getLocalPort(),
                "/api/accounts/" + accountId + "/transactions").body());
        assertEquals(1, firstPage.get("page").asInt());
        assertEquals(20, firstPage.get("limit").asInt());
        assertEquals(23, firstPage.get("total").asLong());
        assertEquals(20, firstPage.get("items").size());
        assertEquals("history-23", firstPage.get("items").get(0).get("note").asText());
        assertEquals("history-4", firstPage.get("items").get(19).get("note").asText());

        JsonNode secondPage = mapper.readTree(get(client, APP.getLocalPort(),
                "/api/accounts/" + accountId + "/transactions?page=2&limit=20").body());
        assertEquals(2, secondPage.get("page").asInt());
        assertEquals(3, secondPage.get("items").size());
        assertEquals("history-3", secondPage.get("items").get(0).get("note").asText());
        assertEquals("history-1", secondPage.get("items").get(2).get("note").asText());

        JsonNode cappedPage = mapper.readTree(get(client, APP.getLocalPort(),
                "/api/accounts/" + accountId + "/transactions?limit=1000").body());
        assertEquals(100, cappedPage.get("limit").asInt());
        assertEquals(23, cappedPage.get("items").size());

        JsonNode pastEndPage = mapper.readTree(get(client, APP.getLocalPort(),
                "/api/accounts/" + accountId + "/transactions?page=3&limit=20").body());
        assertEquals(23, pastEndPage.get("total").asLong());
        assertEquals(0, pastEndPage.get("items").size());

        for (String query : List.of("?page=0", "?limit=0", "?page=invalid", "?limit=invalid")) {
            HttpResponse<String> invalid = get(client, APP.getLocalPort(),
                    "/api/accounts/" + accountId + "/transactions" + query);
            assertEquals(400, invalid.statusCode());
            assertEquals("VALIDATION_ERROR", mapper.readTree(invalid.body()).get("code").asText());
        }

        String emptyEmail = "empty-history-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> emptyCreated = post(client, APP.getLocalPort(), "/api/accounts",
                "{\"name\":\"Empty history\",\"email\":\"" + emptyEmail + "\"}");
        UUID emptyAccountId = UUID.fromString(mapper.readTree(emptyCreated.body()).get("id").asText());
        JsonNode emptyPage = mapper.readTree(get(client, APP.getLocalPort(),
                "/api/accounts/" + emptyAccountId + "/transactions").body());
        assertEquals(0, emptyPage.get("total").asLong());
        assertEquals(0, emptyPage.get("items").size());
    }

    @Test
    void fiftyParallelWithdrawalsCannotOverdraw() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> created = post(client, APP.getLocalPort(), "/api/accounts",
                "{\"name\":\"Concurrent account\",\"email\":\"" + email + "\",\"initial_deposit\":1.00}");
        UUID accountId = UUID.fromString(mapper.readTree(created.body()).get("id").asText());

        int requests = 50;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start timed out");
                    }
                    return post(client, APP.getLocalPort(), "/api/accounts/" + accountId + "/withdraw",
                            "{\"amount\":0.10}").statusCode();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successful = 0;
            int insufficient = 0;
            for (Future<Integer> response : responses) {
                int status = response.get(60, TimeUnit.SECONDS);
                if (status == 201) {
                    successful++;
                } else if (status == 422) {
                    insufficient++;
                }
            }
            assertEquals(10, successful);
            assertEquals(40, insufficient);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        JsonNode account = mapper.readTree(get(client, APP.getLocalPort(), "/api/accounts/" + accountId).body());
        assertEquals(0, account.get("balance").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(0, ledgerBalance(accountId));
        assertEquals(10, transactionCount(accountId, "WITHDRAWAL"));
        assertEquals(11, transactionCount(accountId));
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

    private static int transactionCount(UUID accountId) throws SQLException {
        return transactionCount(accountId, null);
    }

    private static int transactionCount(UUID accountId, String type) throws SQLException {
        String query = type == null
                ? "SELECT COUNT(*) FROM transactions WHERE account_id = ?"
                : "SELECT COUNT(*) FROM transactions WHERE account_id = ? AND type = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setObject(1, accountId);
            if (type != null) {
                statement.setString(2, type);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static long ledgerBalance(UUID accountId) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COALESCE(SUM(CASE
                         WHEN type IN ('DEPOSIT', 'TRANSFER_IN') THEN amount_cents
                         ELSE -amount_cents
                     END), 0)
                     FROM transactions WHERE account_id = ?
                     """)) {
            statement.setObject(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
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

    private static void insertHistoryTransactions(UUID accountId, int count) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO transactions (id, account_id, type, amount_cents, note, created_at)
                     VALUES (?, ?, 'DEPOSIT', 100, ?, ?)
                     """)) {
            Instant base = Instant.now();
            for (int i = 1; i <= count; i++) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, accountId);
                statement.setString(3, "history-" + i);
                statement.setTimestamp(4, Timestamp.from(base.plusSeconds(i)));
                statement.addBatch();
            }
            statement.executeBatch();
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
