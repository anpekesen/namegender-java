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

/** Salutations against a local stand-in that answers from a queue of scripted responses. */
class SalutationTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private record Seen(String method, String path, String body) {}
  private record Reply(int status, String body) {}

  private HttpServer server;
  private final List<Seen> seen = new ArrayList<>();
  private final Deque<Reply> replies = new ArrayDeque<>();
  private NameGender client;

  private static final String ANNA = "{\"query\":\"Dr. Anna Müller\",\"language\":\"de\",\"form\":\"gendered\",\"reason\":null,"
    + "\"salutation\":{\"formal\":\"Sehr geehrte Frau Dr. Müller,\",\"informal\":\"Liebe Anna,\",\"neutral\":\"Guten Tag Dr. Anna Müller,\"},"
    + "\"parts\":{\"opening\":\"Sehr geehrte\",\"courtesy\":\"Frau\",\"academic\":\"Dr.\",\"name\":\"Müller\"},"
    + "\"gender\":\"female\",\"gender_source\":\"lookup\",\"probability\":99,\"confidence\":\"high\","
    + "\"first_name\":\"Anna\",\"last_name\":\"Müller\",\"name_type\":\"personal\",\"country\":\"DE\"}";
  private static final String ALEX = "{\"query\":\"Alex Weber\",\"language\":\"de\",\"form\":\"neutral\",\"reason\":\"below_min_probability\","
    + "\"salutation\":{\"formal\":\"Guten Tag Alex Weber,\",\"informal\":\"Hallo Alex,\",\"neutral\":\"Guten Tag Alex Weber,\"},"
    + "\"parts\":{\"opening\":\"Guten Tag\",\"courtesy\":null,\"academic\":null,\"name\":\"Alex Weber\"},"
    + "\"gender\":null,\"gender_source\":null,\"probability\":null,\"confidence\":null,"
    + "\"first_name\":\"Alex\",\"last_name\":\"Weber\",\"name_type\":\"personal\",\"country\":null}";
  private static final String ACME = "{\"query\":\"Acme GmbH\",\"language\":\"de\",\"form\":\"organization\",\"reason\":null,"
    + "\"salutation\":{\"formal\":\"Sehr geehrte Damen und Herren,\",\"informal\":\"Hallo zusammen,\",\"neutral\":\"Sehr geehrte Damen und Herren,\"},"
    + "\"parts\":{\"opening\":\"Sehr geehrte Damen und Herren\",\"courtesy\":null,\"academic\":null,\"name\":null},"
    + "\"gender\":null,\"gender_source\":null,\"probability\":null,\"confidence\":null,"
    + "\"first_name\":null,\"last_name\":null,\"name_type\":\"organization\",\"country\":null}";

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
    replies.add(new Reply(200, envelope(ANNA)));

    client.salutation("Dr. Anna Müller", SalutationOptions.none());

    assertEquals("POST", seen.get(0).method());
    assertEquals("/api/v1/salutation", seen.get(0).path());
    assertEquals(List.of("name"), fields(lastBody()));
  }

  @Test
  void sendsEveryOptionUnderItsApiName() throws IOException {
    replies.add(new Reply(200, envelope(ANNA)));

    client.salutation("Anna Müller", SalutationOptions.none().language("de").country("DE").locale("de-AT").ip("203.0.113.7")
      .gender("female").minProbability(80).title("Dr."));

    JsonNode body = lastBody();
    assertEquals(List.of("name", "language", "country", "locale", "ip", "gender", "min_probability", "title"), fields(body));
    assertEquals("de", body.get("language").asText());
    assertEquals("female", body.get("gender").asText());
    assertEquals(80, body.get("min_probability").asInt());
    assertTrue(body.get("min_probability").isInt());
    assertEquals("Dr.", body.get("title").asText());
    assertFalse(body.has("best_guess"));
    assertFalse(body.has("ai_fallback"));
  }

  @Test
  void blankOptionsAreLeftOut() throws IOException {
    replies.add(new Reply(200, envelope(ANNA)));

    client.salutation("Anna Müller", SalutationOptions.none().language(" ").title("").gender(null));

    assertEquals(List.of("name"), fields(lastBody()));
  }

  @Test
  void firstAndLastNameAreSentInsteadOfName() throws IOException {
    replies.add(new Reply(200, envelope(ANNA)));
    replies.add(new Reply(200, envelope(ANNA)));

    client.salutation("Ahmet", "Yılmaz", SalutationOptions.none().language("tr"));
    JsonNode both = lastBody();
    client.salutation(null, "Müller", null);
    JsonNode lastOnly = lastBody();

    assertEquals(List.of("first_name", "last_name", "language"), fields(both));
    assertEquals("Yılmaz", both.get("last_name").asText());
    assertEquals(List.of("last_name"), fields(lastOnly));
    assertThrows(IllegalArgumentException.class, () -> client.salutation(null, null, null));
  }

  @Test
  void readsAGenderedSalutation() {
    replies.add(new Reply(200, envelope(ANNA)));

    SalutationResult result = client.salutation("Dr. Anna Müller", SalutationOptions.none().language("de"));

    assertEquals("Sehr geehrte Frau Dr. Müller,", result.salutation().formal());
    assertEquals("Liebe Anna,", result.salutation().informal());
    assertEquals("Guten Tag Dr. Anna Müller,", result.salutation().neutral());
    assertEquals("gendered", result.form());
    assertNull(result.reason());
    assertEquals("Frau", result.parts().courtesy());
    assertEquals("Dr.", result.parts().academic());
    assertEquals("female", result.gender());
    assertEquals("lookup", result.genderSource());
    assertEquals(99, result.probability());
    assertEquals("personal", result.nameType());
    assertEquals("DE", result.country());
    assertEquals("country", result.countrySource());
    assertEquals(1, result.creditsCharged());
    assertEquals(4999, result.creditsRemaining());
    assertEquals("2026.10", result.dataVersion());
    assertEquals("req_1", result.requestId());
  }

  @Test
  void readsANeutralSalutationWithNullParts() {
    replies.add(new Reply(200, envelope(ALEX)));

    SalutationResult result = client.salutation("Alex Weber", SalutationOptions.none());

    assertEquals("neutral", result.form());
    assertEquals("below_min_probability", result.reason());
    assertEquals("Guten Tag Alex Weber,", result.salutation().formal());
    assertNull(result.parts().courtesy());
    assertNull(result.parts().academic());
    assertNull(result.gender());
    assertNull(result.genderSource());
    assertNull(result.probability());
    assertNull(result.country());
  }

  @Test
  void bulkSendsTheListAndKeepsTheOrder() throws IOException {
    replies.add(new Reply(200, "{\"credits_charged\":3,\"credits_remaining\":4996,\"data_version\":\"2026.10\",\"request_id\":\"req_2\","
      + "\"took_ms\":4,\"country_source\":null,\"language\":\"de\","
      + "\"summary\":{\"total\":3,\"gendered\":1,\"neutral\":1,\"organization\":1},"
      + "\"results\":[" + ANNA + "," + ALEX + "," + ACME + "]}"));

    SalutationBulkResult result = client.salutationBulk(List.of("Dr. Anna Müller", "Alex Weber", "Acme GmbH"),
      SalutationOptions.none().language("de").minProbability(95));

    assertEquals("/api/v1/salutation/bulk", seen.get(0).path());
    JsonNode body = lastBody();
    assertEquals(List.of("names", "language", "min_probability"), fields(body));
    assertEquals(3, body.get("names").size());
    assertEquals("Acme GmbH", body.get("names").get(2).asText());

    assertEquals(List.of("Dr. Anna Müller", "Alex Weber", "Acme GmbH"), result.results().stream().map(SalutationResult::query).toList());
    assertEquals(List.of("gendered", "neutral", "organization"), result.results().stream().map(SalutationResult::form).toList());
    assertNull(result.results().get(2).parts().name());
    assertEquals(new SalutationSummary(3, 1, 1, 1), result.summary());
    assertEquals("de", result.language());
    assertEquals(3, result.creditsCharged());
    assertEquals(4, result.tookMs());
    assertNull(result.countrySource());
  }

  @Test
  void anUnsupportedLanguageThrowsTheApiError() {
    replies.add(new Reply(422, "{\"error\":\"invalid_input\",\"message\":\"Unsupported language.\",\"field\":\"language\","
      + "\"supported\":[\"en\",\"de\",\"tr\"],\"request_id\":\"req_3\"}"));

    NameGenderException error = assertThrows(NameGenderException.class,
      () -> client.salutation("Anna Müller", SalutationOptions.none().language("xx")));

    assertEquals(422, error.status());
    assertTrue(error.getMessage().contains("invalid_input"));
    assertTrue(error.getMessage().contains("\"field\":\"language\""));
  }

  @Test
  void optionsAreImmutable() {
    SalutationOptions base = SalutationOptions.none();
    SalutationOptions changed = base.language("tr");

    assertNull(base.language());
    assertEquals("tr", changed.language());
  }
}
