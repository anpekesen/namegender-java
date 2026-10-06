package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One reason behind a name check, e.g. {@code keyboard_pattern} on the first name "asdf".
 * {@code severity} is high, medium, low, info or positive; {@code part} is full,
 * first_name, last_name or null; {@code value} is the text it is about, or null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NameCheckSignal(String code, String severity, String part, String value) {}
