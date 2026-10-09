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

/** Age estimates against a local stand-in that answers from a queue of scripted responses. */
class AgeTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private record Seen(String method, String path, String body) {}
  private record Reply(int status, String body) {}

  private HttpServer server;
  private final List<Seen> seen = new ArrayList<>();
  private final Deque<Reply> replies = new ArrayDeque<>();
  private NameGender client;


  private static final String BRITTANY = "{\"name\":\"Brittany\",\"first_name\":\"Brittany\",\"gender\":null,\"age\":36,"
    + "\"age_range\":{\"low\":32,\"high\":38},\"age_range_80\":{\"low\":28,\"high\":41},"
    + "\"birth_year\":1990,\"sample_size\":353775,\"births\":361434,\"country\":\"US\",\"country_source\":\"default\","
    + "\"source\":\"ssa\",\"series\":\"1880-2024\",\"reference_year\":2026,\"reason\":null}";
  private static final String ANNA_DE = "{\"name\":\"Anna\",\"first_name\":\"Anna\",\"gender\":null,\"age\":null,"
    + "\"age_range\":null,\"age_range_80\":null,\"birth_year\":null,\"sample_size\":0,\"births\":0,"
    + "\"country\":\"DE\",\"country_source\":\"country\",\"source\":null,\"series\":null,\"reference_year\":2026,"
    + "\"reason\":\"country_not_covered\"}";
  private static final String XQZV = "{\"name\":\"Xqzv\",\"first_name\":null,\"gender\":\"female\",\"age\":null,"
    + "\"age_range\":null,\"age_range_80\":null,\"birth_year\":null,\"sample_size\":0,\"births\":0,"
    + "\"country\":\"US\",\"country_source\":\"locale\",\"source\":null,\"series\":null,\"reference_year\":2026,"
    + "\"reason\":\"not_found\"}";

  private static String envelope(int charged, String item) {
    return "{\"credits_charged\":" + charged + ",\"credits_remaining\":49999,\"request_id\":\"req_1\"," + item.substring(1);
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
    replies.add(new Reply(200, envelope(1, BRITTANY)));
    replies.add(new Reply(200, envelope(1, BRITTANY)));

    client.age("Brittany", AgeOptions.none());
    JsonNode none = lastBody();
    client.age("Brittany", null);

    assertEquals("POST", seen.get(0).method());
    assertEquals("/api/v1/age", seen.get(0).path());
    assertEquals(List.of("name"), fields(none));
    assertEquals(List.of("name"), fields(lastBody()));
    assertThrows(IllegalArgumentException.class, () -> client.age(null, AgeOptions.none()));
  }

  @Test
  void sendsEveryOptionUnderItsApiName() throws IOException {
    replies.add(new Reply(200, envelope(1, BRITTANY)));

    client.age("Brittany", AgeOptions.none().gender("female").country("US").locale("en-US").ip("203.0.113.7"));

    JsonNode body = lastBody();
    assertEquals(List.of("name", "gender", "country", "locale", "ip"), fields(body));
    assertEquals("female", body.get("gender").asText());
    assertEquals("US", body.get("country").asText());
    assertEquals("en-US", body.get("locale").asText());
    assertEquals("203.0.113.7", body.get("ip").asText());
  }

  @Test
  void blankOptionsAreLeftOut() throws IOException {
    replies.add(new Reply(200, envelope(1, BRITTANY)));

    client.age("Brittany", AgeOptions.none().gender("").country(" ").locale(null).ip(""));

    assertEquals(List.of("name"), fields(lastBody()));
  }

  @Test
  void readsAFullEstimate() {
    replies.add(new Reply(200, envelope(1, BRITTANY)));

    AgeResult result = client.age("Brittany", AgeOptions.none());

    assertEquals("Brittany", result.name());
    assertEquals("Brittany", result.firstName());
    assertNull(result.gender());
    assertEquals(36, result.age());
    assertEquals(new AgeRange(32, 38), result.ageRange());
    assertEquals(new AgeRange(28, 41), result.ageRange80());
    assertEquals(1990, result.birthYear());
    assertEquals(353775, result.sampleSize());
    assertEquals(361434, result.births());
    assertEquals("US", result.country());
    assertEquals("default", result.countrySource());
    assertEquals("ssa", result.source());
    assertEquals("1880-2024", result.series());
    assertEquals(2026, result.referenceYear());
    assertNull(result.reason());
    assertEquals(1, result.creditsCharged());
    assertEquals(49999, result.creditsRemaining());
    assertEquals("req_1", result.requestId());
  }

  @Test
  void anUncoveredCountryIsAnAnswerWithoutAnAge() {
    replies.add(new Reply(200, envelope(0, ANNA_DE)));

    AgeResult result = client.age("Anna", AgeOptions.none().country("DE"));

    assertNull(result.age());
    assertNull(result.ageRange());
    assertNull(result.ageRange80());
    assertNull(result.birthYear());
    assertNull(result.source());
    assertNull(result.series());
    assertEquals("country_not_covered", result.reason());
    assertEquals("DE", result.country());
    assertEquals(0, result.creditsCharged());
  }

  @Test
  void bulkSendsTheListAndKeepsTheOrder() throws IOException {
    replies.add(new Reply(200, "{\"credits_charged\":2,\"credits_remaining\":49997,\"request_id\":\"req_2\","
      + "\"country_source\":\"locale\",\"results\":[" + BRITTANY + "," + XQZV + "]}"));

    AgeBulkResult result = client.ageBulk(List.of("Brittany", "Xqzv"), AgeOptions.none().gender("female").locale("en-US"));

    assertEquals("/api/v1/age/bulk", seen.get(0).path());
    JsonNode body = lastBody();
    assertEquals(List.of("names", "gender", "locale"), fields(body));
    assertEquals(2, body.get("names").size());
    assertEquals("Xqzv", body.get("names").get(1).asText());

    assertEquals(List.of("Brittany", "Xqzv"), result.results().stream().map(AgeResult::name).toList());
    assertEquals(36, result.results().get(0).age());
    assertEquals(new AgeRange(28, 41), result.results().get(0).ageRange80());
    assertNull(result.results().get(1).age());
    assertNull(result.results().get(1).firstName());
    assertEquals("not_found", result.results().get(1).reason());
    assertEquals(2, result.creditsCharged());
    assertEquals(49997, result.creditsRemaining());
    assertEquals("req_2", result.requestId());
    assertEquals("locale", result.countrySource());
  }

  @Test
  void anApiErrorIsThrown() {
    replies.add(new Reply(402, "{\"error\":\"no_credits\",\"message\":\"No credits left.\",\"request_id\":\"req_3\"}"));

    NameGenderException error = assertThrows(NameGenderException.class,
      () -> client.age("Brittany", AgeOptions.none()));

    assertEquals(402, error.status());
    assertTrue(error.getMessage().contains("no_credits"));
  }

  @Test
  void optionsAreImmutable() {
    AgeOptions base = AgeOptions.none();
    AgeOptions changed = base.gender("male");

    assertNull(base.gender());
    assertEquals("male", changed.gender());
  }
}
