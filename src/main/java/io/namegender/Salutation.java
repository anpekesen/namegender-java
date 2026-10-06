package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The salutation in three registers, e.g. "Sehr geehrte Frau Dr. Müller,", "Liebe Anna," and "Guten Tag Dr. Anna Müller,". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Salutation(String formal, String informal, String neutral) {}
