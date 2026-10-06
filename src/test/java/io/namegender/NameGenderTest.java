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
    "/api/v1/gender/bulk", "{\"results\":[" + result("Ayşe", "female") + "," + result("Acme Ltd", "unknown").replace("\"source\"", "\"name_type\":\"organization\",\"source\"") + "],\"summary\":{\"total\":1,\"identified\":1,\"unknown\":0,\"match_rate\":100},\"took_ms\":3,\"credits_charged\":1,\"credits_remaining\":98}",
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
  void localeAndIpAreSentWhenSet() throws IOException {
    client.name("Andrea", Options.none().locale("it-IT").ip("203.0.113.7"));

    JsonNode body = lastBody();
    assertEquals("it-IT", body.get("locale").asText());
    assertEquals("203.0.113.7", body.get("ip").asText());
    assertFalse(body.has("country"));
  }

  @Test
  void localeAndIpAreLeftOutWhenUnsetOrBlank() throws IOException {
    client.name("Andrea", Options.none().country("IT"));
    JsonNode unset = lastBody();
    client.email("andrea@example.com", Options.none().locale(" ").ip(""));
    JsonNode blank = lastBody();

    assertFalse(unset.has("locale"));
    assertFalse(unset.has("ip"));
    assertFalse(blank.has("locale"));
    assertFalse(blank.has("ip"));
  }

  @Test
  void bulkSendsLocaleAndIp() throws IOException {
    client.bulk(List.of("Andrea"), Options.none().locale("pt_BR").ip("2001:db8::1"));

    JsonNode body = lastBody();
    assertEquals("pt_BR", body.get("locale").asText());
    assertEquals("2001:db8::1", body.get("ip").asText());
  }

  @Test
  void countrySourceIsReadWhenPresentAndNullOtherwise() throws IOException {
    Result fromLocale = JSON.readValue(result("Andrea", "female").replace("\"source\"", "\"country_source\":\"locale\",\"source\""), Result.class);
    Result explicitNull = JSON.readValue(result("Andrea", "female").replace("\"source\"", "\"country_source\":null,\"source\""), Result.class);
    Result absent = JSON.readValue(result("Andrea", "female"), Result.class);

    assertEquals("locale", fromLocale.countrySource());
    assertNull(explicitNull.countrySource());
    assertNull(absent.countrySource());
  }

  @Test
  void bulkEnvelopeCarriesCountrySource() throws IOException {
    BulkResult fromIp = JSON.readValue("{\"results\":[" + result("Andrea", "female")
      + "],\"took_ms\":3,\"credits_charged\":1,\"credits_remaining\":98,\"country_source\":\"ip\"}", BulkResult.class);
    BulkResult none = JSON.readValue("{\"results\":[],\"took_ms\":1,\"country_source\":null}", BulkResult.class);

    assertEquals("ip", fromIp.countrySource());
    assertNull(fromIp.results().get(0).countrySource()); // only the envelope carries it
    assertNull(none.countrySource());
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
  void nameTypeIsReadWhenPresentAndNullWhenAbsent() throws IOException {
    Result organization = JSON.readValue(result("Acme Ltd", "unknown").replace("\"source\"", "\"name_type\":\"organization\",\"source\""), Result.class);
    Result absent = JSON.readValue(result("Ayşe", "female"), Result.class);

    assertEquals("organization", organization.nameType());
    assertNull(absent.nameType());
  }

  @Test
  void bulkResultsCarryNameType() {
    BulkResult result = client.bulk(List.of("Ayşe", "Acme Ltd"), (String) null);

    assertNull(result.results().get(0).nameType());
    assertEquals("organization", result.results().get(1).nameType());
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
