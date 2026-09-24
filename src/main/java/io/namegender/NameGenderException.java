package io.namegender;
public class NameGenderException extends RuntimeException {
  private final int status;
  public NameGenderException(String message, int status) { super(message); this.status = status; }
  public int status() { return status; }
}
