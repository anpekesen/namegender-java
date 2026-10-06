package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What the first-name lookup found. {@code firstNameStatus} is counted, attested,
 * not_found or null (no first name to look up).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NameCheckEvidence(
  @JsonProperty("first_name_status") String firstNameStatus,
  @JsonProperty("first_name_counted_records") long firstNameCountedRecords
) {}
