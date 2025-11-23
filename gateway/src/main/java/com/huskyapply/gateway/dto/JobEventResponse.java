package com.huskyapply.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for job event information. Represents a single event in the job application
 * timeline.
 */
public class JobEventResponse {

  @JsonProperty("event_id")
  private UUID eventId;

  @JsonProperty("job_id")
  private UUID jobId;

  @JsonProperty("event_type")
  private String eventType;

  @JsonProperty("event_status")
  private String eventStatus;

  @JsonProperty("event_date")
  private Instant eventDate;

  @JsonProperty("title")
  private String title;

  @JsonProperty("description")
  private String description;

  @JsonProperty("location")
  private String location;

  @JsonProperty("attendees")
  private List<String> attendees;

  @JsonProperty("duration_minutes")
  private Integer durationMinutes;

  @JsonProperty("outcome")
  private String outcome;

  @JsonProperty("outcome_notes")
  private String outcomeNotes;

  @JsonProperty("reminder_enabled")
  private Boolean reminderEnabled;

  @JsonProperty("reminder_minutes_before")
  private Integer reminderMinutesBefore;

  @JsonProperty("created_at")
  private Instant createdAt;

  @JsonProperty("updated_at")
  private Instant updatedAt;

  @JsonProperty("is_overdue")
  private Boolean isOverdue;

  @JsonProperty("time_until_event")
  private String timeUntilEvent; // Human readable: "2 hours", "3 days", etc.

  public JobEventResponse() {}

  public JobEventResponse(
      UUID eventId,
      UUID jobId,
      String eventType,
      String eventStatus,
      Instant eventDate,
      String title) {
    this.eventId = eventId;
    this.jobId = jobId;
    this.eventType = eventType;
    this.eventStatus = eventStatus;
    this.eventDate = eventDate;
    this.title = title;
  }

  // Getters and Setters
  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getEventStatus() {
    return eventStatus;
  }

  public void setEventStatus(String eventStatus) {
    this.eventStatus = eventStatus;
  }

  public Instant getEventDate() {
    return eventDate;
  }

  public void setEventDate(Instant eventDate) {
    this.eventDate = eventDate;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public List<String> getAttendees() {
    return attendees;
  }

  public void setAttendees(List<String> attendees) {
    this.attendees = attendees;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public String getOutcomeNotes() {
    return outcomeNotes;
  }

  public void setOutcomeNotes(String outcomeNotes) {
    this.outcomeNotes = outcomeNotes;
  }

  public Boolean getReminderEnabled() {
    return reminderEnabled;
  }

  public void setReminderEnabled(Boolean reminderEnabled) {
    this.reminderEnabled = reminderEnabled;
  }

  public Integer getReminderMinutesBefore() {
    return reminderMinutesBefore;
  }

  public void setReminderMinutesBefore(Integer reminderMinutesBefore) {
    this.reminderMinutesBefore = reminderMinutesBefore;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Boolean getIsOverdue() {
    return isOverdue;
  }

  public void setIsOverdue(Boolean isOverdue) {
    this.isOverdue = isOverdue;
  }

  public String getTimeUntilEvent() {
    return timeUntilEvent;
  }

  public void setTimeUntilEvent(String timeUntilEvent) {
    this.timeUntilEvent = timeUntilEvent;
  }

  @Override
  public String toString() {
    return "JobEventResponse{"
        + "eventId="
        + eventId
        + ", eventType='"
        + eventType
        + '\''
        + ", eventStatus='"
        + eventStatus
        + '\''
        + ", title='"
        + title
        + '\''
        + ", eventDate="
        + eventDate
        + '}';
  }
}
