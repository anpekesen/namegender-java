package io.namegender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class NameGender {
  private final String apiKey, baseUrl; private final HttpClient http; private final ObjectMapper json = new ObjectMapper();
  public NameGender(String apiKey) { this(apiKey, "https://namegender.com/api/v1", HttpClient.newHttpClient()); }
  public NameGender(String apiKey, String baseUrl, HttpClient http) {
    if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey is required");
    this.apiKey = apiKey; this.baseUrl = baseUrl.replaceAll("/$", ""); this.http = http;
  }
  public Result name(String name, String country) { return name(name, Options.none().country(country)); }
  public Result name(String name, Options options) { return parse(post("/gender", "{\"name\":"+quote(name)+options(options)+"}"), Result.class); }
  public Result email(String email, String country) { return email(email, Options.none().country(country)); }
  public Result email(String email, Options options) { return parse(post("/gender/email", "{\"email\":"+quote(email)+options(options)+"}"), Result.class); }
  public Result username(String username, String country) { return username(username, Options.none().country(country)); }
  public Result username(String username, Options options) { return parse(post("/gender/username", "{\"username\":"+quote(username)+options(options)+"}"), Result.class); }
  public BulkResult bulk(Collection<String> names, String country) { return bulk(names, Options.none().country(country)); }
  public BulkResult bulk(Collection<String> names, Options options) {
    String values = names.stream().map(NameGender::quote).reduce((a,b)->a+","+b).orElse("");
    return parse(post("/gender/bulk", "{\"names\":["+values+"]"+options(options)+"}"), BulkResult.class);
  }
  public CountriesResult countries(String name, Integer limit) {
    return parse(post("/gender/countries", "{\"name\":"+quote(name)+(limit == null ? "" : ",\"limit\":"+limit)+"}"), CountriesResult.class);
  }
  /** Remaining credits and today's free quota. Costs no credits. */
  public Account account() { return parse(send(request("/me").GET().build()), Account.class); }

  // ---- File jobs ---------------------------------------------------------

  private static final Set<String> FINISHED = Set.of("completed", "failed", "cancelled");
  // Statuses worth retrying an upload for: the request may never have reached
  // the application. Everything else (402, 422, 429 too_many_batches) would
  // fail the same way again.
  private static final Set<Integer> RETRYABLE = Set.of(502, 503, 504);

  /**
   * Upload a CSV or XLSX file and, unless {@code start(false)}, start it. The
   * file name's extension tells the API the format.
   *
   * <p>One Idempotency-Key is used for every attempt of this call, so a retry
   * after a dropped connection returns the first job instead of opening a
   * second one and reserving credit twice.
   */
  public BatchJob createBatch(Path file, BatchOptions options) {
    byte[] content;
    try { content = Files.readAllBytes(file); }
    catch (IOException e) { throw new UncheckedIOException(e); }
    return createBatch(content, file.getFileName().toString(), options);
  }

  /** As {@link #createBatch(Path, BatchOptions)}, from bytes. {@code filename} ends in .csv or .xlsx. */
  public BatchJob createBatch(byte[] content, String filename, BatchOptions options) {
    if (filename == null || filename.isBlank()) throw new IllegalArgumentException("filename is required");
    if (options == null) options = BatchOptions.none();
    var fields = new LinkedHashMap<String, String>();
    fields.put("start", String.valueOf(options.start()));
    putSettings(fields, options);

    String boundary = UUID.randomUUID().toString().replace("-", "");
    byte[] body = multipart(boundary, fields, "file", filename, content);
    String key = options.idempotencyKey() != null ? options.idempotencyKey() : UUID.randomUUID().toString();
    HttpRequest request = request("/batches")
      .header("Content-Type", "multipart/form-data; boundary=" + boundary)
      .header("Idempotency-Key", key)
      .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();

    for (int attempt = 0; ; attempt++) {
      try {
        return parse(text(exchange(request)), BatchJob.class);
      } catch (IOException e) {
        if (attempt >= options.retries()) throw new NameGenderException(e.getMessage(), 0);
      } catch (NameGenderException e) {
        if (!RETRYABLE.contains(e.status()) || attempt >= options.retries()) throw e;
      }
      pause(Duration.ofSeconds(1L << attempt));
    }
  }

  /** Start a job uploaded with {@code start(false)}. {@code nameColumn} is required. */
  public BatchJob startBatch(String id, BatchOptions settings) {
    if (settings == null || settings.nameColumn() == null || settings.nameColumn().isBlank()) {
      throw new IllegalArgumentException("nameColumn is required to start a batch");
    }
    var out = new StringBuilder("{\"name_column\":").append(quote(settings.nameColumn()));
    if (settings.countryColumn() != null) out.append(",\"country_column\":").append(quote(settings.countryColumn()));
    if (settings.country() != null && !settings.country().isBlank()) out.append(",\"country\":").append(quote(settings.country()));
    if (settings.aiFallback() != null) out.append(",\"ai_fallback\":").append(settings.aiFallback());
    if (settings.bestGuess() != null) out.append(",\"best_guess\":").append(settings.bestGuess());
    if (settings.deleteAfterDownload() != null) out.append(",\"delete_after_download\":").append(settings.deleteAfterDownload());
    return parse(post("/batches/" + encode(id) + "/start", out.append('}').toString()), BatchJob.class);
  }

  /** As {@link #startBatch(String, BatchOptions)} with only the name column. */
  public BatchJob startBatch(String id, String nameColumn) { return startBatch(id, BatchOptions.none().nameColumn(nameColumn)); }

  public BatchJob getBatch(String id) { return parse(send(request("/batches/" + encode(id)).GET().build()), BatchJob.class); }

  /** Newest first. Includes jobs started from the dashboard. Null leaves the server default. */
  public BatchList listBatches(Integer limit, Integer page) {
    var query = new StringBuilder();
    if (limit != null) query.append("limit=").append(limit);
    if (page != null) query.append(query.length() > 0 ? "&" : "").append("page=").append(page);
    return parse(send(request("/batches" + (query.length() > 0 ? "?" + query : "")).GET().build()), BatchList.class);
  }

  /** Cancel a job that has not started (credit is returned), or delete a finished one. */
  public void cancelBatch(String id) { send(request("/batches/" + encode(id)).DELETE().build()); }

  /**
   * Poll until the job is completed, failed or cancelled (or still only
   * uploaded, which never moves on its own), and return it. A failed job is
   * returned, not thrown: check {@code status()} and {@code error().code()}.
   *
   * @param timeout how long to keep polling; null waits up to an hour
   * @param onProgress called with every polled job; may be null
   * @throws NameGenderException with status 0 when the timeout runs out
   */
  public BatchJob waitBatch(String id, Duration timeout, Consumer<BatchJob> onProgress) {
    long deadline = System.nanoTime() + (timeout == null ? Duration.ofHours(1) : timeout).toNanos();
    for (;;) {
      BatchJob job = getBatch(id);
      if (onProgress != null) onProgress.accept(job);
      if (FINISHED.contains(job.status()) || "uploaded".equals(job.status())) return job;
      Duration wait = Duration.ofSeconds(job.pollAfterSeconds() == null ? 5 : job.pollAfterSeconds());
      if (System.nanoTime() + wait.toNanos() - deadline > 0) throw new NameGenderException("Timed out waiting for " + id, 0);
      pause(wait);
    }
  }

  /** As {@link #waitBatch(String, Duration, Consumer)}, up to an hour, without progress callbacks. */
  public BatchJob waitBatch(String id) { return waitBatch(id, null, null); }

  /** The result file, in the format that was uploaded. A CSV starts with a UTF-8 byte order mark. */
  public byte[] downloadBatch(String id) {
    try { return exchange(request("/batches/" + encode(id) + "/result").GET().build()).body(); }
    catch (IOException e) { throw new NameGenderException(e.getMessage(), 0); }
  }

  /** Writes the result file to {@code target} and returns it. */
  public Path downloadBatch(String id, Path target) {
    try { return Files.write(target, downloadBatch(id)); }
    catch (IOException e) { throw new UncheckedIOException(e); }
  }

  private static void putSettings(Map<String, String> fields, BatchOptions o) {
    if (o.nameColumn() != null) fields.put("name_column", o.nameColumn());
    if (o.countryColumn() != null) fields.put("country_column", o.countryColumn());
    if (o.country() != null && !o.country().isBlank()) fields.put("country", o.country());
    if (o.aiFallback() != null) fields.put("ai_fallback", String.valueOf(o.aiFallback()));
    if (o.bestGuess() != null) fields.put("best_guess", String.valueOf(o.bestGuess()));
    if (o.deleteAfterDownload() != null) fields.put("delete_after_download", String.valueOf(o.deleteAfterDownload()));
  }

  private static byte[] multipart(String boundary, Map<String, String> fields, String fileField, String filename, byte[] content) {
    var head = new StringBuilder();
    fields.forEach((k, v) -> head.append("--").append(boundary).append("\r\n")
      .append("Content-Disposition: form-data; name=\"").append(k).append("\"\r\n\r\n")
      .append(v).append("\r\n"));
    head.append("--").append(boundary).append("\r\n")
      .append("Content-Disposition: form-data; name=\"").append(fileField).append("\"; filename=\"").append(headerValue(filename)).append("\"\r\n")
      .append("Content-Type: application/octet-stream\r\n\r\n");
    var out = new ByteArrayOutputStream(content.length + head.length() + 64);
    out.writeBytes(head.toString().getBytes(StandardCharsets.UTF_8));
    out.writeBytes(content);
    out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  // A quote or line break in a file name would end the header early.
  private static String headerValue(String value) {
    return value.replace("\\", "\\\\").replace("\"", "%22").replace("\r", "").replace("\n", "");
  }

  private static String encode(String id) {
    if (id == null || id.isEmpty()) throw new IllegalArgumentException("id is required");
    return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static void pause(Duration duration) {
    try { Thread.sleep(duration.toMillis()); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new NameGenderException("Request interrupted", 0); }
  }
  private <T> T parse(String body, Class<T> type) {
    try { return json.readValue(body, type); }
    catch (JsonProcessingException e) { throw new NameGenderException("NameGender returned invalid JSON", 0); }
  }
  private String post(String path, String json) {
    return send(request(path).header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
  }
  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(30))
      .header("Authorization", "Bearer "+apiKey).header("Accept", "application/json");
  }
  private String send(HttpRequest request) {
    try { return text(exchange(request)); }
    catch (IOException e) { throw new NameGenderException(e.getMessage(), 0); }
  }
  // Network errors stay IOExceptions here so that createBatch can tell them from HTTP errors.
  private HttpResponse<byte[]> exchange(HttpRequest request) throws IOException {
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw new NameGenderException(text(response), response.statusCode());
      return response;
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new NameGenderException("Request interrupted", 0); }
  }
  private static String text(HttpResponse<byte[]> response) { return new String(response.body(), StandardCharsets.UTF_8); }
  private static String options(Options options) {
    if (options == null) return "";
    var out = new StringBuilder();
    if (options.country() != null && !options.country().isBlank()) out.append(",\"country\":").append(quote(options.country()));
    if (options.locale() != null && !options.locale().isBlank()) out.append(",\"locale\":").append(quote(options.locale()));
    if (options.ip() != null && !options.ip().isBlank()) out.append(",\"ip\":").append(quote(options.ip()));
    if (options.aiFallback()) out.append(",\"ai_fallback\":true");
    if (options.bestGuess()) out.append(",\"best_guess\":true");
    return out.toString();
  }
  // Every control character is escaped, not just \n and \r: a tab or another
  // character below U+0020 inside a name otherwise produces invalid JSON.
  private static String quote(String value) {
    var out = new StringBuilder(value.length() + 2).append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
          else out.append(c);
        }
      }
    }
    return out.append('"').toString();
  }
}
