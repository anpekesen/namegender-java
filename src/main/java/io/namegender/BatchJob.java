package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A file job, as {@code GET /batches/{id}} returns it and as a {@code batch.*}
 * webhook carries it. Nullable fields are boxed.
 *
 * <p>{@code status} is one of {@code uploaded}, {@code queued}, {@code processing},
 * {@code completed}, {@code failed} or {@code cancelled}. A failed job has
 * {@code error()} set; branch on its {@code code()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchJob(
  String id,
  String status,
  String source,
  File file,
  Columns columns,
  JobOptions options,
  Rows rows,
  int progress,
  Credits credits,
  Summary summary,
  @JsonProperty("data_version") String dataVersion,
  Error error,
  ResultFile result,
  Inspection inspection,
  @JsonProperty("poll_after_seconds") Integer pollAfterSeconds,
  @JsonProperty("created_at") String createdAt,
  @JsonProperty("started_at") String startedAt,
  @JsonProperty("finished_at") String finishedAt,
  @JsonProperty("expires_at") String expiresAt
) {
  /** The uploaded file. {@code format} is {@code csv} or {@code xlsx}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record File(String name, String format) {}

  /** The name and country columns the job reads; null until chosen. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Columns(String name, String country) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record JobOptions(
    String country,
    @JsonProperty("ai_fallback") boolean aiFallback,
    @JsonProperty("best_guess") boolean bestGuess,
    @JsonProperty("delete_after_download") boolean deleteAfterDownload
  ) {}

  /** {@code identified} is set once the job has completed. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Rows(int total, int processed, Integer identified) {}

  /** {@code reserved} is held when processing begins; {@code charged} is null until completed. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Credits(Integer reserved, Integer charged) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Summary(int male, int female, int unknown, @JsonProperty("from_llm") int fromLlm) {}

  /**
   * Why a job failed: {@code source_missing}, {@code no_columns}, {@code name_column_missing},
   * {@code empty_file}, {@code bad_format}, {@code unreadable}, {@code no_credits},
   * {@code processing_error} or {@code stalled}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Error(String code, String message) {}

  /** Where the result is. {@code url} needs the API key; use {@code downloadBatch}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ResultFile(String url, String format, @JsonProperty("expires_at") String expiresAt) {}

  /** Only while {@code status} is {@code uploaded}: the columns, a preview and the cost. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Inspection(
    List<String> columns,
    List<List<String>> preview,
    @JsonProperty("guessed_name_column") String guessedNameColumn,
    @JsonProperty("guessed_country_column") String guessedCountryColumn,
    @JsonProperty("credits_needed") int creditsNeeded,
    @JsonProperty("credits_available") int creditsAvailable
  ) {}
}
