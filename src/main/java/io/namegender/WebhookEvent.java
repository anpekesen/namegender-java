package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A verified webhook request, as {@link Webhooks#verify} returns it.
 *
 * <p>{@code type} is {@code batch.completed}, {@code batch.failed}, {@code credits.low},
 * {@code credits.depleted} or {@code webhook.test}. New types can be added: answer 2xx
 * to one you do not handle and ignore it. {@code id} is stable across retries;
 * deduplicate on it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookEvent(
  String id,
  String type,
  @JsonProperty("created_at") String createdAt,
  @JsonProperty("api_version") String apiVersion,
  Data data
) {
  private static final ObjectMapper JSON = new ObjectMapper();

  /** {@code object} is the job, the credits alert or, for {@code webhook.test}, a message. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Data(JsonNode object) {}

  /** The raw {@code data.object}. */
  public JsonNode object() { return data == null ? null : data.object(); }

  /** {@code data.object} of {@code batch.completed} and {@code batch.failed}. */
  public BatchJob batchJob() { return as(BatchJob.class); }

  /** {@code data.object} of {@code credits.low} and {@code credits.depleted}. */
  public CreditsAlert creditsAlert() { return as(CreditsAlert.class); }

  private <T> T as(Class<T> type) {
    JsonNode object = object();
    if (object == null || object.isNull()) return null;
    try { return JSON.treeToValue(object, type); }
    catch (JsonProcessingException e) { throw new NameGenderException("Webhook data.object is not a " + type.getSimpleName(), 0); }
  }
}
