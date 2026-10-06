package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One salutation. {@code form} is gendered, neutral or organization; when it is
 * not gendered, {@code reason} says why (gender_unknown, below_min_probability,
 * gender_neutral_requested, no_surname, no_given_name, language_ungendered).
 *
 * <p>In a {@link SalutationBulkResult} the credit and request fields are 0 or null:
 * read them on the bulk result instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalutationResult(
  String query,
  String language,
  String form,
  String reason,
  Salutation salutation,
  SalutationParts parts,
  /** male, female or null. */
  String gender,
  /** lookup, input, title or null. */
  @JsonProperty("gender_source") String genderSource,
  Integer probability,
  String confidence,
  @JsonProperty("first_name") String firstName,
  @JsonProperty("last_name") String lastName,
  /** personal, organization or role. */
  @JsonProperty("name_type") String nameType,
  String country,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("data_version") String dataVersion,
  @JsonProperty("request_id") String requestId,
  /** Where the country came from: country, locale or ip; null when none was used. */
  @JsonProperty("country_source") String countrySource
) {}
