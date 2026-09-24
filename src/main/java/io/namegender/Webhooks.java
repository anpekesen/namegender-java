package io.namegender;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook signature check.
 *
 * <p>Pass the body exactly as received, as bytes. A framework that parses the
 * JSON and serialises it again changes the bytes, and the signature no longer
 * matches.
 */
public final class Webhooks {
  /** Five minutes, as the API documents. */
  public static final long DEFAULT_TOLERANCE_SECONDS = 300;

  private static final ObjectMapper JSON = new ObjectMapper();

  private Webhooks() {}

  /**
   * Checks {@code NameGender-Signature} and returns the parsed event. During a
   * secret rotation the header carries two {@code v1} values; either one
   * matching is enough.
   *
   * @throws WebhookVerificationException if the header is missing or malformed,
   *   the timestamp is more than five minutes off, or no signature matches
   */
  public static WebhookEvent verify(byte[] payload, String signatureHeader, String secret) {
    return verify(payload, signatureHeader, secret, DEFAULT_TOLERANCE_SECONDS, System.currentTimeMillis() / 1000);
  }

  /** As {@link #verify(byte[], String, String)}, with the tolerance and the clock given. */
  public static WebhookEvent verify(byte[] payload, String signatureHeader, String secret, long toleranceSeconds, long nowEpochSeconds) {
    if (secret == null || secret.isEmpty()) throw new IllegalArgumentException("secret is required");
    if (payload == null) throw new IllegalArgumentException("payload must be the raw request body");
    if (signatureHeader == null || signatureHeader.isBlank()) throw new WebhookVerificationException("Missing NameGender-Signature header");

    Long timestamp = null;
    List<String> signatures = new ArrayList<>();
    for (String part : signatureHeader.split(",")) {
      String[] pair = part.trim().split("=", 2);
      String value = pair.length == 2 ? pair[1] : "";
      if (pair[0].equals("t") && value.matches("\\d{1,18}")) timestamp = Long.parseLong(value);
      else if (pair[0].equals("v1") && !value.isEmpty()) signatures.add(value);
    }
    if (timestamp == null || signatures.isEmpty()) throw new WebhookVerificationException("Malformed NameGender-Signature header");
    if (Math.abs(nowEpochSeconds - timestamp) > toleranceSeconds) {
      throw new WebhookVerificationException("Webhook timestamp is outside the tolerance window");
    }

    byte[] expected = HexFormat.of().formatHex(sign(secret, timestamp, payload)).getBytes(StandardCharsets.US_ASCII);
    boolean matched = false;
    // Every candidate is compared, in constant time, so the answer does not
    // reveal which one matched or how much of it did.
    for (String signature : signatures) {
      matched |= MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }
    if (!matched) throw new WebhookVerificationException("Webhook signature does not match");

    try { return JSON.readValue(payload, WebhookEvent.class); }
    catch (IOException e) { throw new WebhookVerificationException("Webhook body is not valid JSON"); }
  }

  private static byte[] sign(String secret, long timestamp, byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
      return mac.doFinal(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is not available", e);
    }
  }
}
