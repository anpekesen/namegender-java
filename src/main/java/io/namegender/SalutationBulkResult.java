package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A bulk salutation. Results are in the order the names were sent. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalutationBulkResult(
  List<SalutationResult> results,
  SalutationSummary summary,
  String language,
  @JsonProperty("took_ms") int tookMs,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("data_version") String dataVersion,
  @JsonProperty("request_id") String requestId,
  /** Where the request's country came from: country, locale or ip; null when none was used. */
  @JsonProperty("country_source") String countrySource
) {}
