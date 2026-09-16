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
  @JsonProperty("request_id") String requestId
) {}
