package com.huskyapply.gateway.exception;

public class SubscriptionLimitExceededException extends RuntimeException {
  public SubscriptionLimitExceededException(String message) {
    super(message);
  }
}
