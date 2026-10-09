package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A bulk age estimate. Results are in the order the names were sent. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgeBulkResult(
  List<AgeResult> results,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("request_id") String requestId,
  /** Where the request's country came from: country, locale, ip or default (no hint, US data). */
  @JsonProperty("country_source") String countrySource
) {}
