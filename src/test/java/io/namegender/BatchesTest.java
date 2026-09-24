package io.namegender;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** File jobs against a local stand-in that answers from a queue of scripted responses. */
class BatchesTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private record Seen(String method, String uri, String contentType, String idempotencyKey, byte[] body) {
    String text() { return new String(body, StandardCharsets.UTF_8); }
  }
  private record Reply(int status, byte[] body) {
    static Reply json(int status, String body) { return new Reply(status, body.getBytes(StandardCharsets.UTF_8)); }
  }

  private HttpServer server;
  private final List<Seen> seen = new ArrayList<>();
  private final Deque<Reply> replies = new ArrayDeque<>();
  private NameGender client;

  private static String job(String status, Integer pollAfter) {
    return "{\"id\":\"B-7K2M9QX4TA\",\"status\":\"" + status + "\",\"source\":\"api\","
      + "\"file\":{\"name\":\"people.csv\",\"format\":\"csv\"},\"columns\":{\"name\":\"first_name\",\"country\":null},"
      + "\"options\":{\"country\":null,\"ai_fallback\":false,\"best_guess\":true,\"delete_after_download\":false},"
      + "\"rows\":{\"total\":3,\"processed\":3,\"identified\":null},\"progress\":100,"
      + "\"credits\":{\"reserved\":3,\"charged\":null},"
      + "\"summary\":" + ("completed".equals(status) ? "{\"male\":1,\"female\":2,\"unknown\":0,\"from_llm\":0}" : "null") + ","
      + "\"data_version\":null,\"error\":null,\"result\":null,\"inspection\":null,"
      + "\"poll_after_seconds\":" + pollAfter + ",\"created_at\":\"2026-09-24T10:00:00Z\",\"started_at\":null,\"finished_at\":null,\"expires_at\":null}";
  }

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      var headers = exchange.getRequestHeaders();
      seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath()
        + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()),
        headers.getFirst("Content-Type"), headers.getFirst("Idempotency-Key"), exchange.getRequestBody().readAllBytes()));
      Reply reply = replies.isEmpty() ? Reply.json(500, "{\"error\":\"unexpected\"}") : replies.poll();
      if (reply.status() == 204) {
        exchange.sendResponseHeaders(204, -1);
      } else {
        exchange.sendResponseHeaders(reply.status(), reply.body().length);
        exchange.getResponseBody().write(reply.body());
      }
      exchange.close();
    });
    server.start();
    client = new NameGender("ng_live_test", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1", HttpClient.newHttpClient());
  }

  @AfterEach
  void stop() { server.stop(0); }

  @Test
  void createSendsMultipartWithFieldsFileAndAKey(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("people.csv");
    Files.write(file, "id,first_name\n1,Ayşe\n".getBytes(StandardCharsets.UTF_8));
    replies.add(Reply.json(202, job("queued", 2)));

    BatchJob job = client.createBatch(file, BatchOptions.none().nameColumn("first_name").country("TR").bestGuess(true).aiFallback(false));

    Seen request = seen.get(0);
    assertEquals("POST", request.method());
    assertEquals("/api/v1/batches", request.uri());
    assertTrue(request.contentType().startsWith("multipart/form-data; boundary="));
    assertNotNull(request.idempotencyKey());
    String body = request.text();
    assertTrue(body.contains("name=\"start\"\r\n\r\ntrue\r\n"));
    assertTrue(body.contains("name=\"name_column\"\r\n\r\nfirst_name\r\n"));
    assertTrue(body.contains("name=\"country\"\r\n\r\nTR\r\n"));
    assertTrue(body.contains("name=\"best_guess\"\r\n\r\ntrue\r\n"));
    assertTrue(body.contains("name=\"ai_fallback\"\r\n\r\nfalse\r\n"));
    assertFalse(body.contains("country_column"));
    assertFalse(body.contains("delete_after_download"));
    assertTrue(body.contains("name=\"file\"; filename=\"people.csv\""));
    assertTrue(body.contains("id,first_name\n1,Ayşe\n"));

    assertEquals("B-7K2M9QX4TA", job.id());
    assertEquals("queued", job.status());
    assertEquals("first_name", job.columns().name());
    assertNull(job.columns().country());
    assertNull(job.credits().charged());
    assertTrue(job.options().bestGuess());
  }

  @Test
  void aRetryAfter503ReusesTheSameKey() {
    replies.add(Reply.json(503, "{\"error\":\"unavailable\"}"));
    replies.add(Reply.json(202, job("queued", 2)));

    BatchJob job = client.createBatch("a\nAli\n".getBytes(StandardCharsets.UTF_8), "names.csv",
      BatchOptions.none().start(false).idempotencyKey("my-key-1"));

    assertEquals(2, seen.size());
    assertEquals("my-key-1", seen.get(0).idempotencyKey());
    assertEquals("my-key-1", seen.get(1).idempotencyKey());
    assertTrue(seen.get(1).text().contains("name=\"start\"\r\n\r\nfalse\r\n"));
    assertEquals("queued", job.status());
  }

  @Test
  void aGeneratedKeyIsAlsoKeptAcrossRetries() {
    replies.add(Reply.json(502, "{}"));
    replies.add(Reply.json(202, job("queued", 2)));

    client.createBatch(new byte[] {1}, "names.xlsx", BatchOptions.none().nameColumn("n"));

    assertNotNull(seen.get(0).idempotencyKey());
    assertEquals(seen.get(0).idempotencyKey(), seen.get(1).idempotencyKey());
  }

  @Test
  void a402IsNotRetried() {
    replies.add(Reply.json(402, "{\"error\":\"no_credits\",\"message\":\"Out of credits.\"}"));
    replies.add(Reply.json(202, job("queued", 2)));

    NameGenderException error = assertThrows(NameGenderException.class,
      () -> client.createBatch(new byte[] {1}, "names.csv", BatchOptions.none().nameColumn("n")));

    assertEquals(402, error.status());
    assertEquals(1, seen.size());
  }

  @Test
  void startSendsTheSettingsAsJson() throws IOException {
    replies.add(Reply.json(202, job("queued", 2)));

    client.startBatch("B-7K2M9QX4TA", BatchOptions.none().nameColumn("first_name").countryColumn("country").bestGuess(true));

    assertEquals("POST", seen.get(0).method());
    assertEquals("/api/v1/batches/B-7K2M9QX4TA/start", seen.get(0).uri());
    var body = JSON.readTree(seen.get(0).body());
    assertEquals("first_name", body.get("name_column").asText());
    assertEquals("country", body.get("country_column").asText());
    assertTrue(body.get("best_guess").asBoolean());
    assertFalse(body.has("start"));
    assertThrows(IllegalArgumentException.class, () -> client.startBatch("B-1", BatchOptions.none()));
  }

  @Test
  void waitPollsUntilTheJobIsFinished() {
    replies.add(Reply.json(200, job("queued", 0)));
    replies.add(Reply.json(200, job("processing", 0)));
    replies.add(Reply.json(200, job("completed", null)));
    var progress = new ArrayList<String>();

    BatchJob done = client.waitBatch("B-7K2M9QX4TA", Duration.ofSeconds(5), j -> progress.add(j.status()));

    assertEquals(List.of("queued", "processing", "completed"), progress);
    assertEquals(3, seen.size());
    assertTrue(seen.stream().allMatch(s -> s.method().equals("GET") && s.uri().equals("/api/v1/batches/B-7K2M9QX4TA")));
    assertEquals(2, done.summary().female());
  }

  @Test
  void waitReturnsAFailedJobAndTimesOut() {
    replies.add(Reply.json(200, job("failed", null)));
    assertEquals("failed", client.waitBatch("B-7K2M9QX4TA", Duration.ofSeconds(1), null).status());

    replies.add(Reply.json(200, job("processing", 5)));
    NameGenderException error = assertThrows(NameGenderException.class,
      () -> client.waitBatch("B-7K2M9QX4TA", Duration.ofSeconds(1), null));
    assertEquals(0, error.status());
  }

  @Test
  void cancelListAndDownloadUseTheRightMethodAndUrl() {
    replies.add(new Reply(204, new byte[0]));
    replies.add(Reply.json(200, "{\"data\":[" + job("completed", null) + "],\"page\":2,\"per_page\":10,\"total\":11,\"has_more\":false}"));
    byte[] csv = "﻿id,gender\n1,female\n".getBytes(StandardCharsets.UTF_8);
    replies.add(new Reply(200, csv));

    client.cancelBatch("B-7K2M9QX4TA");
    BatchList list = client.listBatches(10, 2);
    byte[] file = client.downloadBatch("B-7K2M9QX4TA");

    assertEquals("DELETE", seen.get(0).method());
    assertEquals("/api/v1/batches/B-7K2M9QX4TA", seen.get(0).uri());
    assertEquals("GET", seen.get(1).method());
    assertEquals("/api/v1/batches?limit=10&page=2", seen.get(1).uri());
    assertEquals(11, list.total());
    assertEquals("completed", list.data().get(0).status());
    assertEquals("GET", seen.get(2).method());
    assertEquals("/api/v1/batches/B-7K2M9QX4TA/result", seen.get(2).uri());
    assertArrayEquals(csv, file);
  }

  @Test
  void idsAreUrlEncoded() {
    replies.add(Reply.json(200, job("queued", 2)));

    client.getBatch("a/b c?");

    assertEquals("/api/v1/batches/a%2Fb%20c%3F", seen.get(0).uri());
  }
}
