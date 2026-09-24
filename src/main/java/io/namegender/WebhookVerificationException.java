package io.namegender;

/** The request is not a genuine NameGender webhook. Answer it with 400 and do nothing else. */
public final class WebhookVerificationException extends NameGenderException {
  public WebhookVerificationException(String message) { super(message, 0); }
}
