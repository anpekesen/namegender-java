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
  public Result name(String name, String country) { return parse(post("/gender", "{\"name\":"+quote(name)+optionalCountry(country)+"}"), Result.class); }
  public Result email(String email, String country) { return parse(post("/gender/email", "{\"email\":"+quote(email)+optionalCountry(country)+"}"), Result.class); }
  public Result username(String username, String country) { return parse(post("/gender/username", "{\"username\":"+quote(username)+optionalCountry(country)+"}"), Result.class); }
  public BulkResult bulk(Collection<String> names, String country) {
    String values = names.stream().map(NameGender::quote).reduce((a,b)->a+","+b).orElse("");
    return parse(post("/gender/bulk", "{\"names\":["+values+"]"+optionalCountry(country)+"}"), BulkResult.class);
  }
  public CountriesResult countries(String name, Integer limit) {
    return parse(post("/gender/countries", "{\"name\":"+quote(name)+(limit == null ? "" : ",\"limit\":"+limit)+"}"), CountriesResult.class);
  }
  private <T> T parse(String body, Class<T> type) {
    try { return json.readValue(body, type); }
    catch (JsonProcessingException e) { throw new NameGenderException("NameGender returned invalid JSON", 0); }
  }
  private String post(String path, String json) {
    var request = HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(30))
      .header("Authorization", "Bearer "+apiKey).header("Accept", "application/json").header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw new NameGenderException(response.body(), response.statusCode());
      return response.body();
    } catch (IOException e) { throw new NameGenderException(e.getMessage(), 0); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new NameGenderException("Request interrupted", 0); }
  }
  private static String optionalCountry(String country) { return country == null || country.isBlank() ? "" : ",\"country\":"+quote(country); }
  private static String quote(String value) { return "\""+value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")+"\""; }
}
