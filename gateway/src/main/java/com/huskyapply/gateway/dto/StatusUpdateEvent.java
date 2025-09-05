package com.huskyapply.gateway.dto;

import java.time.Instant;
import java.util.Map;

public class StatusUpdateEvent {
  private String status;
  private String content;
  private String generatedText;
  private String message;
  private Instant timestamp;
  private Map<String, Object> streamingData;

  public StatusUpdateEvent() {
    this.timestamp = Instant.now();
  }

  public StatusUpdateEvent(String status) {
    this.status = status;
    this.timestamp = Instant.now();
  }

  public StatusUpdateEvent(String status, String content) {
    this.status = status;
    this.content = content;
    this.timestamp = Instant.now();
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getGeneratedText() {
    return generatedText;
  }

  public void setGeneratedText(String generatedText) {
    this.generatedText = generatedText;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public Map<String, Object> getStreamingData() {
    return streamingData;
  }

  public void setStreamingData(Map<String, Object> streamingData) {
    this.streamingData = streamingData;
  }
}
