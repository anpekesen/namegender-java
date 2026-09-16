package io.namegender;

/**
 * Optional request settings for name, email, username and bulk lookups.
 *
 * <pre>{@code
 * client.name("Andrea", Options.none().country("IT").bestGuess(true));
 * }</pre>
 *
 * Immutable: every method returns a new instance.
 */
public final class Options {
  private static final Options NONE = new Options(null, false, false);

  private final String country;
  private final boolean aiFallback;
  private final boolean bestGuess;

  private Options(String country, boolean aiFallback, boolean bestGuess) {
    this.country = country;
    this.aiFallback = aiFallback;
    this.bestGuess = bestGuess;
  }

  /** No country and both switches off: the API defaults. */
  public static Options none() { return NONE; }

  /** Two-letter ISO 3166-1 code; weights the answer by that country's data. Null or blank sends none. */
  public Options country(String country) { return new Options(country, aiFallback, bestGuess); }

  /**
   * Ask a language model when the name is not in the dataset. The account must
   * have given AI consent in the dashboard, otherwise the API answers 422
   * {@code ai_consent_required}.
   */
  public Options aiFallback(boolean aiFallback) { return new Options(country, aiFallback, bestGuess); }

  /** Return the likelier gender even below the confidence threshold instead of "unknown". */
  public Options bestGuess(boolean bestGuess) { return new Options(country, aiFallback, bestGuess); }

  public String country() { return country; }
  public boolean aiFallback() { return aiFallback; }
  public boolean bestGuess() { return bestGuess; }
}
