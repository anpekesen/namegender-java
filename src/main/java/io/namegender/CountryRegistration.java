package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One counted country. {@code share} is a percentage of the registrations in this list alone. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountryRegistration(
  String country,
  int count,
  double share,
  String gender,
  int probability,
  String source
) {}
