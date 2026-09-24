package io.namegender;

/**
 * Settings for {@link NameGender#createBatch} and {@link NameGender#startBatch}.
 *
 * <pre>{@code
 * client.createBatch(Path.of("customers.csv"),
 *   BatchOptions.none().nameColumn("first_name").countryColumn("country"));
 * }</pre>
 *
 * Immutable: every method returns a new instance. A switch left unset is not
 * sent, so the API default applies.
 */
public final class BatchOptions {
  private static final BatchOptions NONE = new BatchOptions(null, null, null, null, null, null, true, null, 2);

  private final String nameColumn, countryColumn, country;
  private final Boolean aiFallback, bestGuess, deleteAfterDownload;
  private final boolean start;
  private final String idempotencyKey;
  private final int retries;

  private BatchOptions(String nameColumn, String countryColumn, String country, Boolean aiFallback, Boolean bestGuess,
                       Boolean deleteAfterDownload, boolean start, String idempotencyKey, int retries) {
    this.nameColumn = nameColumn;
    this.countryColumn = countryColumn;
    this.country = country;
    this.aiFallback = aiFallback;
    this.bestGuess = bestGuess;
    this.deleteAfterDownload = deleteAfterDownload;
    this.start = start;
    this.idempotencyKey = idempotencyKey;
    this.retries = retries;
  }

  /** Nothing set, {@code start} true, two retries: the defaults. */
  public static BatchOptions none() { return NONE; }

  /**
   * Header of the column holding the names, exactly as in the file. Required to
   * start: a guessed column that is wrong would spend credits on the wrong data.
   */
  public BatchOptions nameColumn(String v) { return new BatchOptions(v, countryColumn, country, aiFallback, bestGuess, deleteAfterDownload, start, idempotencyKey, retries); }

  /** Header of a column holding a country code per row. */
  public BatchOptions countryColumn(String v) { return new BatchOptions(nameColumn, v, country, aiFallback, bestGuess, deleteAfterDownload, start, idempotencyKey, retries); }

  /** Default country (ISO 3166-1 alpha-2) for rows without one. */
  public BatchOptions country(String v) { return new BatchOptions(nameColumn, countryColumn, v, aiFallback, bestGuess, deleteAfterDownload, start, idempotencyKey, retries); }

  /** Ask a language model for names not in the dataset. Needs AI consent on the account. */
  public BatchOptions aiFallback(boolean v) { return new BatchOptions(nameColumn, countryColumn, country, v, bestGuess, deleteAfterDownload, start, idempotencyKey, retries); }

  /** Return the likelier gender even when the evidence is weak. */
  public BatchOptions bestGuess(boolean v) { return new BatchOptions(nameColumn, countryColumn, country, aiFallback, v, deleteAfterDownload, start, idempotencyKey, retries); }

  /** The result can be downloaded once, then it is deleted. */
  public BatchOptions deleteAfterDownload(boolean v) { return new BatchOptions(nameColumn, countryColumn, country, aiFallback, bestGuess, v, start, idempotencyKey, retries); }

  /**
   * {@code false}: upload and inspect only; the job carries {@code inspection}
   * (columns, preview, cost) and is started with {@link NameGender#startBatch}.
   * Default {@code true}. Ignored by {@code startBatch}.
   */
  public BatchOptions start(boolean v) { return new BatchOptions(nameColumn, countryColumn, country, aiFallback, bestGuess, deleteAfterDownload, v, idempotencyKey, retries); }

  /**
   * Your own Idempotency-Key, to keep the no-second-job guarantee across your
   * own retries. By default a new one is generated per {@code createBatch} call.
   */
  public BatchOptions idempotencyKey(String v) { return new BatchOptions(nameColumn, countryColumn, country, aiFallback, bestGuess, deleteAfterDownload, start, v, retries); }

  /** Extra attempts after a network error or 502/503/504. Default 2. */
  public BatchOptions retries(int v) {
    if (v < 0) throw new IllegalArgumentException("retries must be 0 or more");
    return new BatchOptions(nameColumn, countryColumn, country, aiFallback, bestGuess, deleteAfterDownload, start, idempotencyKey, v);
  }

  public String nameColumn() { return nameColumn; }
  public String countryColumn() { return countryColumn; }
  public String country() { return country; }
  public Boolean aiFallback() { return aiFallback; }
  public Boolean bestGuess() { return bestGuess; }
  public Boolean deleteAfterDownload() { return deleteAfterDownload; }
  public boolean start() { return start; }
  public String idempotencyKey() { return idempotencyKey; }
  public int retries() { return retries; }
}
