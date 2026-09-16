package io.namegender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;

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
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw new NameGenderException(response.body(), response.statusCode());
      return response.body();
    } catch (IOException e) { throw new NameGenderException(e.getMessage(), 0); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new NameGenderException("Request interrupted", 0); }
  }
  private static String options(Options options) {
    if (options == null) return "";
    var out = new StringBuilder();
    if (options.country() != null && !options.country().isBlank()) out.append(",\"country\":").append(quote(options.country()));
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
