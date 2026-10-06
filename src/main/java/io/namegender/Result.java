package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One lookup. Success is carried by the HTTP status: a non-2xx response throws
 * {@link NameGenderException} instead of returning a result.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Result(
  String query,
  String name,
  String gender,
  String country,
  int probability,
  @JsonProperty("sample_size") int sampleSize,
  @JsonProperty("took_ms") int tookMs,
  String confidence,
  String source,
  @JsonProperty("matched_as") String matchedAs,
  @JsonProperty("first_name") String firstName,
  @JsonProperty("middle_name") String middleName,
  @JsonProperty("last_name") String lastName,
  /** personal, organization or role; null when the API does not say. */
  @JsonProperty("name_type") String nameType,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("data_version") String dataVersion,
  @JsonProperty("request_id") String requestId,
  /**
   * Where the country came from: country, locale or ip; null when none was used.
   * Always null on bulk items: read {@link BulkResult#countrySource()} instead.
   */
  @JsonProperty("country_source") String countrySource
) {
  /** The 0.5 shape, without {@code countrySource}. */
  public Result(String query, String name, String gender, String country, int probability, int sampleSize, int tookMs,
                String confidence, String source, String matchedAs, String firstName, String middleName, String lastName,
                String nameType, int creditsCharged, int creditsRemaining, String dataVersion, String requestId) {
    this(query, name, gender, country, probability, sampleSize, tookMs, confidence, source, matchedAs, firstName,
      middleName, lastName, nameType, creditsCharged, creditsRemaining, dataVersion, requestId, null);
  }
}
