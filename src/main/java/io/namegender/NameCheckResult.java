package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Whether a name typed into a form looks like a real person's name. {@code assessment}
 * is plausible, suspicious or implausible and {@code score} runs from 0 to 100;
 * {@code signals} say why. It never calls a name fake: use it to flag records for a
 * look, not to reject people automatically. Surnames are judged by their shape only.
 *
 * <p>In a {@link NameCheckBulkResult} the credit and request fields are 0 or null:
 * read them on the bulk result instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NameCheckResult(
  String query,
  /** plausible, suspicious or implausible. */
  String assessment,
  /** 0 to 100; higher looks more like a real person's name. */
  int score,
  List<NameCheckSignal> signals,
  @JsonProperty("first_name") String firstName,
  @JsonProperty("last_name") String lastName,
  /** personal, organization or role. */
  @JsonProperty("name_type") String nameType,
  NameCheckEvidence evidence,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("data_version") String dataVersion,
  @JsonProperty("request_id") String requestId,
  /** Where the country came from: country, locale or ip; null when none was used. */
  @JsonProperty("country_source") String countrySource
) {}
