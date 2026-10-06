package io.namegender;

/**
 * Optional settings for {@link NameGender#salutation} and {@link NameGender#salutationBulk}.
 *
 * <pre>{@code
 * client.salutation("Dr. Anna Müller", SalutationOptions.none().language("de"));
 * }</pre>
 *
 * Immutable: every method returns a new instance. Null or blank values are not sent.
 * There is no {@code bestGuess} or {@code aiFallback} here: a gender that is not
 * certain gives the neutral form instead.
 */
public final class SalutationOptions {
  private static final SalutationOptions NONE = new SalutationOptions(null, null, null, null, null, null, null);

  private final String language;
  private final String country;
  private final String locale;
  private final String ip;
  private final String gender;
  private final Integer minProbability;
  private final String title;

  private SalutationOptions(String language, String country, String locale, String ip, String gender,
                            Integer minProbability, String title) {
    this.language = language;
    this.country = country;
    this.locale = locale;
    this.ip = ip;
    this.gender = gender;
    this.minProbability = minProbability;
    this.title = title;
  }

  /** Nothing set: the API defaults. */
  public static SalutationOptions none() { return NONE; }

  /**
   * Language of the salutation: en, en-US, en-GB, de, de-AT, de-CH, fr, es, it, pt,
   * pt-PT, pt-BR, nl, tr, pl or ja. Unset uses the language of {@link #locale(String)},
   * else the main language of the country, else en. Another value is answered 422.
   */
  public SalutationOptions language(String language) { return new SalutationOptions(language, country, locale, ip, gender, minProbability, title); }

  /** Two-letter ISO 3166-1 code; a country hint for the gender lookup. */
  public SalutationOptions country(String country) { return new SalutationOptions(language, country, locale, ip, gender, minProbability, title); }

  /** A language tag such as {@code de-AT}; a country hint, and the default language. */
  public SalutationOptions locale(String locale) { return new SalutationOptions(language, country, locale, ip, gender, minProbability, title); }

  /** The end user's IP address; a country hint when neither country nor a regional locale is set. Not stored. */
  public SalutationOptions ip(String ip) { return new SalutationOptions(language, country, locale, ip, gender, minProbability, title); }

  /** A gender you already know: male, female or neutral. Overrides the lookup; neutral always gives the neutral form. */
  public SalutationOptions gender(String gender) { return new SalutationOptions(language, country, locale, ip, gender, minProbability, title); }

  /** 50 to 100, default 90. Below it the neutral form is used. Null sends none. */
  public SalutationOptions minProbability(Integer minProbability) { return new SalutationOptions(language, country, locale, ip, gender, minProbability, title); }

  /** An academic title kept in a separate field, such as "Dr."; used in German and English. */
  public SalutationOptions title(String title) { return new SalutationOptions(language, country, locale, ip, gender, minProbability, title); }

  public String language() { return language; }
  public String country() { return country; }
  public String locale() { return locale; }
  public String ip() { return ip; }
  public String gender() { return gender; }
  public Integer minProbability() { return minProbability; }
  public String title() { return title; }
}
