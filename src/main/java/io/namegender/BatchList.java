package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** One page of file jobs, newest first. Includes jobs started from the dashboard. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchList(
  List<BatchJob> data,
  int page,
  @JsonProperty("per_page") int perPage,
  int total,
  @JsonProperty("has_more") boolean hasMore
) {}
