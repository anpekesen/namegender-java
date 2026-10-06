package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The pieces of the formal salutation ("Sehr geehrte", "Frau", "Dr.", "Müller"); each may be null. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalutationParts(String opening, String courtesy, String academic, String name) {}
