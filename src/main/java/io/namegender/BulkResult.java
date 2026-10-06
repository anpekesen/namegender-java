package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BulkResult(
  List<Result> results,
  Map<String, Object> summary,
  @JsonProperty("took_ms") int tookMs,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("data_version") String dataVersion,
  @JsonProperty("request_id") String requestId,
  /** Where the request's country came from: country, locale or ip; null when none was used. */
  @JsonProperty("country_source") String countrySource
) {
  /** The 0.5 shape, without {@code countrySource}. */
  public BulkResult(List<Result> results, Map<String, Object> summary, int tookMs, int creditsCharged,
                    int creditsRemaining, String dataVersion, String requestId) {
    this(results, summary, tookMs, creditsCharged, creditsRemaining, dataVersion, requestId, null);
  }
}
