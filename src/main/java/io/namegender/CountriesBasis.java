package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** What the numbers in a {@link CountriesResult} rest on. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountriesBasis(
  @JsonProperty("counted_sources") List<String> countedSources,
  @JsonProperty("counted_countries") int countedCountries,
  @JsonProperty("attested_countries") int attestedCountries,
  String note
) {}
