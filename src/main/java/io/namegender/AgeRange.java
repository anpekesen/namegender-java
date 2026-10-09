package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A range of ages, both ends inclusive. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgeRange(int low, int high) {}
