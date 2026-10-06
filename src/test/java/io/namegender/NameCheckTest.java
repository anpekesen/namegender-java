package io.namegender;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Name checks against a local stand-in that answers from a queue of scripted responses. */
class NameCheckTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private record Seen(String method, String path, String body) {}
  private record Reply(int status, String body) {}

  private HttpServer server;
  private final List<Seen> seen = new ArrayList<>();
  private final Deque<Reply> replies = new ArrayDeque<>();
  private NameGender client;

  private static final String ASDF = "{\"query\":\"asdf qwerty\",\"assessment\":\"implausible\",\"score\":0,"
    + "\"signals\":[{\"code\":\"keyboard_pattern\",\"severity\":\"high\",\"part\":\"first_name\",\"value\":\"asdf\"},"
    + "{\"code\":\"keyboard_pattern\",\"severity\":\"high\",\"part\":\"last_name\",\"value\":\"qwerty\"},"
    + "{\"code\":\"first_name_not_found\",\"severity\":\"medium\",\"part\":null,\"value\":null}],"
    + "\"first_name\":\"Asdf\",\"last_name\":\"Qwerty\",\"name_type\":\"personal\","
    + "\"evidence\":{\"first_name_status\":\"not_found\",\"first_name_counted_records\":0}}";
  private static final String JENNIFER = "{\"query\":\"Jennifer Null\",\"assessment\":\"plausible\",\"score\":96,"
    + "\"signals\":[{\"code\":\"first_name_attested\",\"severity\":\"positive\",\"part\":\"first_name\",\"value\":\"Jennifer\"}],"
    + "\"first_name\":\"Jennifer\",\"last_name\":\"Null\",\"name_type\":\"personal\","
    + "\"evidence\":{\"first_name_status\":\"counted\",\"first_name_counted_records\":1468211}}";
  private static final String ACME = "{\"query\":\"Acme Ltd\",\"assessment\":\"suspicious\",\"score\":40,"
    + "\"signals\":[{\"code\":\"organization_name\",\"severity\":\"medium\",\"part\":\"full\",\"value\":null}],"
    + "\"first_name\":null,\"last_name\":null,\"name_type\":\"organization\","
    + "\"evidence\":{\"first_name_status\":null,\"first_name_counted_records\":0}}";

  private static String envelope(String item) {
    return "{\"credits_charged\":1,\"credits_remaining\":4999,\"data_version\":\"2026.10\",\"request_id\":\"req_1\",\"country_source\":\"country\","
      + item.substring(1);
  }

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      Reply reply = replies.isEmpty() ? new Reply(500, "{\"error\":\"unexpected\"}") : replies.poll();
      byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(reply.status(), bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    client = new NameGender("ng_live_test", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1", HttpClient.newHttpClient());
  }

  @AfterEach
  void stop() { server.stop(0); }

  private JsonNode lastBody() throws IOException { return JSON.readTree(seen.get(seen.size() - 1).body()); }

  private static List<String> fields(JsonNode body) {
    var out = new ArrayList<String>();
    body.fieldNames().forEachRemaining(out::add);
    return out;
  }

  @Test
  void sendsOnlyTheNameWhenNothingIsSet() throws IOException {
    replies.add(new Reply(200, envelope(ASDF)));

    client.nameCheck("asdf qwerty", NameCheckOptions.none());

    assertEquals("POST", seen.get(0).method());
    assertEquals("/api/v1/name-check", seen.get(0).path());
    assertEquals(List.of("name"), fields(lastBody()));
  }

  @Test
  void sendsEveryOptionUnderItsApiName() throws IOException {
    replies.add(new Reply(200, envelope(ASDF)));

    client.nameCheck("asdf qwerty", NameCheckOptions.none().country("DE").locale("de-AT").ip("203.0.113.7"));

    JsonNode body = lastBody();
    assertEquals(List.of("name", "country", "locale", "ip"), fields(body));
    assertEquals("DE", body.get("country").asText());
    assertEquals("de-AT", body.get("locale").asText());
    assertEquals("203.0.113.7", body.get("ip").asText());
  }

  @Test
  void blankOptionsAreLeftOut() throws IOException {
    replies.add(new Reply(200, envelope(ASDF)));

    client.nameCheck("asdf qwerty", NameCheckOptions.none().country(" ").locale("").ip(null));

    assertEquals(List.of("name"), fields(lastBody()));
  }

  @Test
  void firstAndLastNameAreSentInsteadOfName() throws IOException {
    replies.add(new Reply(200, envelope(JENNIFER)));
    replies.add(new Reply(200, envelope(JENNIFER)));

    client.nameCheck("Jennifer", "Null", NameCheckOptions.none().country("US"));
    JsonNode both = lastBody();
    client.nameCheck("Jennifer", null, null);
    JsonNode firstOnly = lastBody();

    assertEquals(List.of("first_name", "last_name", "country"), fields(both));
    assertEquals("Null", both.get("last_name").asText());
    assertEquals(List.of("first_name"), fields(firstOnly));
    assertThrows(IllegalArgumentException.class, () -> client.nameCheck(null, null, null));
    assertThrows(IllegalArgumentException.class, () -> client.nameCheck(null, NameCheckOptions.none()));
  }

  @Test
  void readsAnImplausibleName() {
    replies.add(new Reply(200, envelope(ASDF)));

    NameCheckResult result = client.nameCheck("asdf qwerty", NameCheckOptions.none());

    assertEquals("asdf qwerty", result.query());
    assertEquals("implausible", result.assessment());
    assertEquals(0, result.score());
    assertEquals(3, result.signals().size());
    assertEquals(new NameCheckSignal("keyboard_pattern", "high", "first_name", "asdf"), result.signals().get(0));
    assertEquals("qwerty", result.signals().get(1).value());
    assertEquals("first_name_not_found", result.signals().get(2).code());
    assertNull(result.signals().get(2).part());
    assertNull(result.signals().get(2).value());
    assertEquals("Asdf", result.firstName());
    assertEquals("Qwerty", result.lastName());
    assertEquals("personal", result.nameType());
    assertEquals("not_found", result.evidence().firstNameStatus());
    assertEquals(0, result.evidence().firstNameCountedRecords());
    assertEquals("country", result.countrySource());
    assertEquals(1, result.creditsCharged());
    assertEquals(4999, result.creditsRemaining());
    assertEquals("2026.10", result.dataVersion());
    assertEquals("req_1", result.requestId());
  }

  @Test
  void readsAPlausibleNameAndNullEvidence() {
    replies.add(new Reply(200, envelope(JENNIFER)));
    replies.add(new Reply(200, envelope(ACME)));

    NameCheckResult jennifer = client.nameCheck("Jennifer Null", NameCheckOptions.none());
    NameCheckResult acme = client.nameCheck("Acme Ltd", NameCheckOptions.none());

    assertEquals("plausible", jennifer.assessment());
    assertEquals(96, jennifer.score());
    assertEquals("positive", jennifer.signals().get(0).severity());
    assertEquals("counted", jennifer.evidence().firstNameStatus());
    assertEquals(1468211, jennifer.evidence().firstNameCountedRecords());

    assertEquals("organization", acme.nameType());
    assertNull(acme.firstName());
    assertNull(acme.lastName());
    assertNull(acme.evidence().firstNameStatus());
    assertNull(acme.signals().get(0).value());
  }

  @Test
  void bulkSendsTheListAndKeepsTheOrder() throws IOException {
    replies.add(new Reply(200, "{\"credits_charged\":3,\"credits_remaining\":4996,\"data_version\":\"2026.10\",\"request_id\":\"req_2\","
      + "\"took_ms\":4,\"country_source\":null,"
      + "\"summary\":{\"total\":3,\"plausible\":1,\"suspicious\":1,\"implausible\":1},"
      + "\"results\":[" + JENNIFER + "," + ACME + "," + ASDF + "]}"));

    NameCheckBulkResult result = client.nameCheckBulk(List.of("Jennifer Null", "Acme Ltd", "asdf qwerty"),
      NameCheckOptions.none().locale("en-US"));

    assertEquals("/api/v1/name-check/bulk", seen.get(0).path());
    JsonNode body = lastBody();
    assertEquals(List.of("names", "locale"), fields(body));
    assertEquals(3, body.get("names").size());
    assertEquals("asdf qwerty", body.get("names").get(2).asText());

    assertEquals(List.of("Jennifer Null", "Acme Ltd", "asdf qwerty"), result.results().stream().map(NameCheckResult::query).toList());
    assertEquals(List.of("plausible", "suspicious", "implausible"), result.results().stream().map(NameCheckResult::assessment).toList());
    assertEquals(new NameCheckSummary(3, 1, 1, 1), result.summary());
    assertEquals(3, result.creditsCharged());
    assertEquals(4996, result.creditsRemaining());
    assertEquals(4, result.tookMs());
    assertEquals("req_2", result.requestId());
    assertNull(result.countrySource());
  }

  @Test
  void anApiErrorIsThrown() {
    replies.add(new Reply(402, "{\"error\":\"no_credits\",\"message\":\"No credits left.\",\"request_id\":\"req_3\"}"));

    NameGenderException error = assertThrows(NameGenderException.class,
      () -> client.nameCheck("Jennifer Null", NameCheckOptions.none()));

    assertEquals(402, error.status());
    assertTrue(error.getMessage().contains("no_credits"));
  }

  @Test
  void optionsAreImmutable() {
    NameCheckOptions base = NameCheckOptions.none();
    NameCheckOptions changed = base.country("TR");

    assertNull(base.country());
    assertEquals("TR", changed.country());
  }
}
