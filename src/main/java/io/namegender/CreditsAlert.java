package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code data.object} of {@code credits.low} and {@code credits.depleted}. Checked
 * hourly; a heads-up, not a balance feed.
 *
 * <p>{@code kind} is {@code credits_low} or {@code credits_out}. {@code creditsRemaining}
 * is the same as on {@code /me}: purchased, subscription and today's free credits.
 * {@code dailyBurn} is the average per day over the last 14 days; {@code runwayDays}
 * is set on {@code credits.low} only.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditsAlert(
  String kind,
  @JsonProperty("credits_remaining") long creditsRemaining,
  long purchased,
  long subscription,
  @JsonProperty("daily_burn") long dailyBurn,
  @JsonProperty("runway_days") Integer runwayDays,
  String since
) {}
