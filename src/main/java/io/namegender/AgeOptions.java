package io.namegender;

/**
 * Optional settings for {@link NameGender#age} and {@link NameGender#ageBulk}.
 *
 * <pre>{@code
 * client.age("Camille", AgeOptions.none().country("FR").gender("female"));
 * }</pre>
 *
 * Immutable: every method returns a new instance. Null or blank values are not sent.
 */
public final class AgeOptions {
  private static final AgeOptions NONE = new AgeOptions(null, null, null, null);

  private final String gender;
  private final String country;
  private final String locale;
  private final String ip;

  private AgeOptions(String gender, String country, String locale, String ip) {
    this.gender = gender;
    this.country = country;
    this.locale = locale;
    this.ip = ip;
  }

  /** Nothing set: the API defaults (US data when there is no country hint). */
  public static AgeOptions none() { return NONE; }

  /** {@code male} or {@code female}: narrows the estimate to one gender's records. */
  public AgeOptions gender(String gender) { return new AgeOptions(gender, country, locale, ip); }

  /** Two-letter ISO 3166-1 code: whose birth records to use. */
  public AgeOptions country(String country) { return new AgeOptions(gender, country, locale, ip); }

  /** A language tag such as {@code fr-FR}; a country hint. */
  public AgeOptions locale(String locale) { return new AgeOptions(gender, country, locale, ip); }

  /** The end user's IP address; a country hint when neither country nor a regional locale is set. Not stored. */
  public AgeOptions ip(String ip) { return new AgeOptions(gender, country, locale, ip); }

  public String gender() { return gender; }
  public String country() { return country; }
  public String locale() { return locale; }
  public String ip() { return ip; }
}
