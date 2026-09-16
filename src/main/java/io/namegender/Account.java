package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The account behind the API key. Reading it costs no credits. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Account(
  String email,
  @JsonProperty("credits_remaining") long creditsRemaining,
  @JsonProperty("purchased_credits") long purchasedCredits,
  @JsonProperty("free_today") long freeToday,
  @JsonProperty("free_daily_limit") long freeDailyLimit,
  @JsonProperty("lifetime_requests") long lifetimeRequests,
  @JsonProperty("data_version") String dataVersion
) {}
