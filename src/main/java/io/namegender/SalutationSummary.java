package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** How many names in a bulk salutation got each form. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalutationSummary(int total, int gendered, int neutral, int organization) {}
