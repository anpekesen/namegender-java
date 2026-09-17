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
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("data_version") String dataVersion,
  @JsonProperty("request_id") String requestId
) {}
