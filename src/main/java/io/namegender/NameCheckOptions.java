package io.namegender;

/**
 * Optional settings for {@link NameGender#nameCheck} and {@link NameGender#nameCheckBulk}.
 *
 * <pre>{@code
 * client.nameCheck("asdf qwerty", NameCheckOptions.none().country("DE"));
 * }</pre>
 *
 * Immutable: every method returns a new instance. Null or blank values are not sent.
 * There is no {@code bestGuess}, {@code aiFallback} or {@code language} here.
 */
public final class NameCheckOptions {
  private static final NameCheckOptions NONE = new NameCheckOptions(null, null, null);

  private final String country;
  private final String locale;
  private final String ip;

  private NameCheckOptions(String country, String locale, String ip) {
    this.country = country;
    this.locale = locale;
    this.ip = ip;
  }

  /** Nothing set: the API defaults. */
  public static NameCheckOptions none() { return NONE; }

  /** Two-letter ISO 3166-1 code; a country hint for the first-name lookup. */
  public NameCheckOptions country(String country) { return new NameCheckOptions(country, locale, ip); }

  /** A language tag such as {@code de-AT}; a country hint. */
  public NameCheckOptions locale(String locale) { return new NameCheckOptions(country, locale, ip); }

  /** The end user's IP address; a country hint when neither country nor a regional locale is set. Not stored. */
  public NameCheckOptions ip(String ip) { return new NameCheckOptions(country, locale, ip); }

  public String country() { return country; }
  public String locale() { return locale; }
  public String ip() { return ip; }
}
