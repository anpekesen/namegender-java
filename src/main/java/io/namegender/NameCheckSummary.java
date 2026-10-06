package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** How many names in a bulk name check got each assessment. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NameCheckSummary(int total, int plausible, int suspicious, int implausible) {}
