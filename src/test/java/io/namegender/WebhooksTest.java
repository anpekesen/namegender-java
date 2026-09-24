package io.namegender;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The vector shared by every NameGender SDK. */
class WebhooksTest {
  private static final String SECRET = "whsec_test_vector";
  private static final byte[] BODY = "{\"id\":\"evt_1\",\"type\":\"webhook.test\"}".getBytes(StandardCharsets.UTF_8);
  private static final String SIG = "857fcddfea47617c448b7a8e6537bbd59c9922a37c5273b2709812fbadb29e50";
  private static final String HEADER = "t=1700000000,v1=" + SIG;
  private static final long NOW = 1700000000L;

  private static WebhookEvent verify(byte[] body, String header, String secret, long now) {
    return Webhooks.verify(body, header, secret, 300, now);
  }

  @Test
  void theSharedVectorVerifies() {
    WebhookEvent event = verify(BODY, HEADER, SECRET, NOW);

    assertEquals("evt_1", event.id());
    assertEquals("webhook.test", event.type());
  }

  @Test
  void anyMatchingV1IsEnoughDuringRotation() {
    String rotated = "t=1700000000,v1=" + "0".repeat(64) + ",v1=" + SIG;

    assertEquals("evt_1", verify(BODY, rotated, SECRET, NOW).id());
  }

  @Test
  void aTamperedBodyFails() {
    byte[] tampered = "{\"id\":\"evt_2\",\"type\":\"webhook.test\"}".getBytes(StandardCharsets.UTF_8);
    assertThrows(WebhookVerificationException.class, () -> verify(tampered, HEADER, SECRET, NOW));
  }

  @Test
  void theWrongSecretFails() {
    assertThrows(WebhookVerificationException.class, () -> verify(BODY, HEADER, "whsec_other", NOW));
  }

  @Test
  void aStaleTimestampFails() {
    assertEquals("evt_1", verify(BODY, HEADER, SECRET, NOW + 300).id());
    assertThrows(WebhookVerificationException.class, () -> verify(BODY, HEADER, SECRET, NOW + 301));
  }

  @Test
  void aMissingOrMalformedHeaderFails() {
    assertThrows(WebhookVerificationException.class, () -> verify(BODY, null, SECRET, NOW));
    assertThrows(WebhookVerificationException.class, () -> verify(BODY, "", SECRET, NOW));
    assertThrows(WebhookVerificationException.class, () -> verify(BODY, "t=abc,v1=", SECRET, NOW));
  }

  @Test
  void aVerificationErrorIsANameGenderException() {
    NameGenderException error = assertThrows(NameGenderException.class, () -> verify(BODY, "t=abc,v1=", SECRET, NOW));
    assertInstanceOf(WebhookVerificationException.class, error);
  }

  @Test
  void theDataObjectReadsAsACreditsAlert() {
    String body = "{\"id\":\"evt_3\",\"type\":\"credits.low\",\"created_at\":\"2026-09-24T10:00:00Z\",\"api_version\":\"v1\","
      + "\"data\":{\"object\":{\"kind\":\"credits_low\",\"credits_remaining\":120,\"purchased\":100,\"subscription\":20,"
      + "\"daily_burn\":60,\"runway_days\":2,\"since\":\"2026-09-24T09:00:00Z\"}}}";
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String header = "t=" + NOW + ",v1=" + hmac(bytes);

    CreditsAlert alert = verify(bytes, header, SECRET, NOW).creditsAlert();

    assertEquals("credits_low", alert.kind());
    assertEquals(120, alert.creditsRemaining());
    assertEquals(2, alert.runwayDays());
  }

  private static String hmac(byte[] body) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update((NOW + ".").getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(mac.doFinal(body));
    } catch (Exception e) { throw new AssertionError(e); }
  }
}
