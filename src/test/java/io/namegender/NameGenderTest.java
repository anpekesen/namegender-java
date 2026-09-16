package io.namegender;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the client against a local stand-in for the API and inspects what it sends. */
class NameGenderTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private record Seen(String method, String path, String auth, String body) {}

  private HttpServer server;
  private final List<Seen> seen = new ArrayList<>();
  private final Map<String, String> responses = Map.of(
    "/api/v1/gender", result("Ayşe", "female"),
    "/api/v1/gender/email", result("mehmet", "male"),
    "/api/v1/gender/username", result("ayse", "female"),
    "/api/v1/gender/bulk", "{\"results\":[" + result("Ayşe", "female") + "],\"summary\":{\"total\":1,\"identified\":1,\"unknown\":0,\"match_rate\":100},\"took_ms\":3,\"credits_charged\":1,\"credits_remaining\":98}",
    "/api/v1/me", "{\"email\":\"dev@example.com\",\"credits_remaining\":1250,\"purchased_credits\":1150,\"free_today\":100,\"free_daily_limit\":100,\"lifetime_requests\":42,\"data_version\":\"2026.08\",\"ai\":{\"consent\":false}}"
  );
  private NameGender client;

  private static String result(String query, String gender) {
    return "{\"query\":\"" + query + "\",\"name\":\"" + query + "\",\"gender\":\"" + gender + "\",\"country\":\"TR\",\"probability\":98,"
      + "\"sample_size\":1200,\"took_ms\":2,\"confidence\":\"high\",\"source\":\"ssa\",\"matched_as\":\"" + query + "\","
      + "\"credits_charged\":1,\"credits_remaining\":99,\"data_version\":\"2026.08\",\"request_id\":\"req_1\"}";
  }

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String path = exchange.getRequestURI().getPath();
      seen.add(new Seen(exchange.getRequestMethod(), path, exchange.getRequestHeaders().getFirst("Authorization"), body));
      String response = responses.get(path);
      int status = 200;
      if (response == null) {
        status = 402;
        response = "{\"error\":\"no_credits\",\"message\":\"Out of credits.\",\"request_id\":\"req_9\"}";
      }
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    client = new NameGender("ng_live_test", baseUrl("/api/v1"), HttpClient.newHttpClient());
  }

  @AfterEach
  void stop() { server.stop(0); }

  private String baseUrl(String path) { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }

  private JsonNode lastBody() throws IOException { return JSON.readTree(seen.get(seen.size() - 1).body()); }

  @Test
  void sendsTheKeyAsBearerAndReadsTheResult() throws IOException {
    Result result = client.name("Ayşe", "TR");

    Seen request = seen.get(0);
    assertEquals("POST", request.method());
    assertEquals("/api/v1/gender", request.path());
    assertEquals("Bearer ng_live_test", request.auth());
    assertEquals("TR", lastBody().get("country").asText());
    assertEquals("female", result.gender());
    assertEquals(1200, result.sampleSize());
    assertEquals(99, result.creditsRemaining());
  }

  @Test
  void theCountryOverloadSendsNoSwitches() throws IOException {
    client.email("mehmet@example.com", (String) null);

    JsonNode body = lastBody();
    assertEquals("/api/v1/gender/email", seen.get(0).path());
    assertFalse(body.has("country"));
    assertFalse(body.has("ai_fallback"));
    assertFalse(body.has("best_guess"));
  }

  @Test
  void optionsAreSentUnderTheirApiNames() throws IOException {
    client.username("ayse_84", Options.none().country("TR").aiFallback(true).bestGuess(true));

    JsonNode body = lastBody();
    assertEquals("/api/v1/gender/username", seen.get(0).path());
    assertEquals("TR", body.get("country").asText());
    assertTrue(body.get("ai_fallback").asBoolean());
    assertTrue(body.get("best_guess").asBoolean());
  }

  @Test
  void optionsAreImmutable() {
    Options base = Options.none();
    Options changed = base.bestGuess(true);

    assertFalse(base.bestGuess());
    assertTrue(changed.bestGuess());
  }

  @Test
  void bulkTakesOptions() throws IOException {
    BulkResult result = client.bulk(List.of("Ayşe"), Options.none().bestGuess(true));

    JsonNode body = lastBody();
    assertTrue(body.get("names").isArray());
    assertEquals("Ayşe", body.get("names").get(0).asText());
    assertTrue(body.get("best_guess").asBoolean());
    assertEquals("female", result.results().get(0).gender());
  }

  @Test
  void accountReadsTheBalanceWithAGet() {
    Account account = client.account();

    assertEquals("GET", seen.get(0).method());
    assertEquals("/api/v1/me", seen.get(0).path());
    assertEquals(1250, account.creditsRemaining());
    assertEquals(100, account.freeDailyLimit());
    assertEquals("2026.08", account.dataVersion());
  }

  @Test
  void controlCharactersInANameStillMakeValidJson() throws IOException {
    String name = "Ay" + '\t' + "şe" + (char) 1 + "\"x\"\\";

    client.name(name, (String) null);

    assertEquals(name, lastBody().get("name").asText());
  }

  @Test
  void aNon2xxResponseThrowsWithStatusAndBody() {
    var bad = new NameGender("ng_live_test", baseUrl("/api/v1/nope"), HttpClient.newHttpClient());

    NameGenderException error = assertThrows(NameGenderException.class, () -> bad.name("Ali", (String) null));
    assertEquals(402, error.status());
    assertTrue(error.getMessage().contains("no_credits"));
  }
}
