package io.namegender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Country distribution of a name. Not a country-of-origin or ethnicity inference:
 * {@code registrations} is counted volume, comparable only among countries that publish
 * counted birth statistics; {@code attestedIn} is presence with no weight attached.
 * Show {@code basis().note()} next to any percentage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountriesResult(
  String name,
  CountriesBasis basis,
  List<CountryRegistration> registrations,
  @JsonProperty("attested_in") List<String> attestedIn,
  @JsonProperty("took_ms") int tookMs,
  @JsonProperty("credits_charged") int creditsCharged,
  @JsonProperty("credits_remaining") int creditsRemaining,
  @JsonProperty("data_version") String dataVersion,
  @JsonProperty("request_id") String requestId
) {}
