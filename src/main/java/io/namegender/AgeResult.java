package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How old the people who carry a first name usually are, from birth records:
 * {@code age} is the median, {@code ageRange} the middle half and {@code ageRange80}
 * the middle 80 percent. It describes a group, not a person: never use it for
 * decisions about an individual.
 *
 * <p>When {@code age} is null, {@code reason} says why: not_found, insufficient_data
 * or country_not_covered (no credit charged). That is a normal answer, not an error.
 *
 * <p>In an {@link AgeBulkResult} the credit and request fields are 0 or null:
 * read them on the bulk result instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgeResult(
  String name,
  @JsonProperty("first_name") String firstName,
  /** male, female or null. */
  String gender,
  /** Median age; null when there is no estimate. */
  Integer age,
  /** The middle half; null when there is no estimate. */
  @JsonProperty("age_range") AgeRange ageRange,
  /** The middle 80 percent; null when there is no estimate. */
  @JsonProperty("age_range_80") AgeRange ageRange80,
  @JsonProperty("birth_year") Integer birthYear,
  @JsonProperty("sample_size") long sampleSize,
  long births,
  String country,
  /** Where the country came from: country, locale, ip or default (no hint, US data). */
  @JsonProperty("country_source") String countrySource,
  String source,
  String series,
  @JsonProperty("reference_year") int referenceYear,
  /** not_found, insufficient_data, country_not_covered or null. */
  String reason,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("request_id") String requestId
) {}
