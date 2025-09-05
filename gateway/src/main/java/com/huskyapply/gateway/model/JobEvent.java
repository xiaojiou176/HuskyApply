package com.huskyapply.gateway.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a job application event in the tracking timeline.
 * Events can include application submission, interviews, phone screens, follow-ups, etc.
 */
@Table("job_events")
public class JobEvent {

  @Id private UUID id;

  @Column("job_id")
  private UUID jobId;

  @Column("event_type")
  private String eventType;

  @Column("event_status")
  private String eventStatus;

  @Column("event_date")
  private Instant eventDate;

  @Column("title")
  private String title;

  @Column("description")
  private String description;

  @Column("location")
  private String location;

  @Column("attendees")
  private List<String> attendees;

  @Column("duration_minutes")
  private Integer durationMinutes;

  @Column("outcome")
  private String outcome;

  @Column("outcome_notes")
  private String outcomeNotes;

  @Column("reminder_enabled")
  private Boolean reminderEnabled;

  @Column("reminder_minutes_before")
  private Integer reminderMinutesBefore;

  @Column("created_at")
  private Instant createdAt;

  @Column("updated_at")
  private Instant updatedAt;

  // Event types enumeration for consistency
  public static final class EventType {
    public static final String APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED";
    public static final String APPLICATION_RECEIVED = "APPLICATION_RECEIVED";
    public static final String PHONE_SCREEN = "PHONE_SCREEN";
    public static final String INTERVIEW_SCHEDULED = "INTERVIEW_SCHEDULED";
    public static final String TECHNICAL_INTERVIEW = "TECHNICAL_INTERVIEW";
    public static final String BEHAVIORAL_INTERVIEW = "BEHAVIORAL_INTERVIEW";
    public static final String FINAL_INTERVIEW = "FINAL_INTERVIEW";
    public static final String REFERENCE_CHECK = "REFERENCE_CHECK";
    public static final String BACKGROUND_CHECK = "BACKGROUND_CHECK";
    public static final String OFFER_RECEIVED = "OFFER_RECEIVED";
    public static final String OFFER_ACCEPTED = "OFFER_ACCEPTED";
    public static final String OFFER_DECLINED = "OFFER_DECLINED";
    public static final String REJECTION_RECEIVED = "REJECTION_RECEIVED";
    public static final String FOLLOW_UP_SENT = "FOLLOW_UP_SENT";
    public static final String THANK_YOU_SENT = "THANK_YOU_SENT";
    public static final String DEADLINE_REMINDER = "DEADLINE_REMINDER";
    public static final String APPLICATION_WITHDRAWN = "APPLICATION_WITHDRAWN";
    
    private EventType() {} // Prevent instantiation
  }

  // Event status enumeration
  public static final class EventStatus {
    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RESCHEDULED = "RESCHEDULED";
    
    private EventStatus() {} // Prevent instantiation
  }

  // Outcome enumeration
  public static final class Outcome {
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String WAITING_FOR_FEEDBACK = "WAITING_FOR_FEEDBACK";
    public static final String RESCHEDULED = "RESCHEDULED";
    
    private Outcome() {} // Prevent instantiation
  }

  public JobEvent() {}

  public JobEvent(
      UUID id,
      UUID jobId,
      String eventType,
      String eventStatus,
      Instant eventDate,
      String title,
      String description) {
    this.id = id;
    this.jobId = jobId;
    this.eventType = eventType;
    this.eventStatus = eventStatus;
    this.eventDate = eventDate;
    this.title = title;
    this.description = description;
    this.reminderEnabled = true;
    this.reminderMinutesBefore = 60; // Default 1 hour
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public static JobEventBuilder builder() {
    return new JobEventBuilder();
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JobEvent jobEvent = (JobEvent) o;
    return Objects.equals(id, jobEvent.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "JobEvent{" +
        "id=" + id +
        ", jobId=" + jobId +
        ", eventType='" + eventType + '\'' +
        ", eventStatus='" + eventStatus + '\'' +
        ", eventDate=" + eventDate +
        ", title='" + title + '\'' +
        ", location='" + location + '\'' +
        '}';
  }

  // Builder Pattern
  public static class JobEventBuilder {
    private UUID id;
    private UUID jobId;
    private String eventType;
    private String eventStatus = EventStatus.PENDING;
    private Instant eventDate;
    private String title;
    private String description;
    private String location;
    private List<String> attendees;
    private Integer durationMinutes;
    private String outcome;
    private String outcomeNotes;
    private Boolean reminderEnabled = true;
    private Integer reminderMinutesBefore = 60;

    public JobEventBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public JobEventBuilder jobId(UUID jobId) {
      this.jobId = jobId;
      return this;
    }

    public JobEventBuilder eventType(String eventType) {
      this.eventType = eventType;
      return this;
    }

    public JobEventBuilder eventStatus(String eventStatus) {
      this.eventStatus = eventStatus;
      return this;
    }

    public JobEventBuilder eventDate(Instant eventDate) {
      this.eventDate = eventDate;
      return this;
    }

    public JobEventBuilder title(String title) {
      this.title = title;
      return this;
    }

    public JobEventBuilder description(String description) {
      this.description = description;
      return this;
    }

    public JobEventBuilder location(String location) {
      this.location = location;
      return this;
    }

    public JobEventBuilder attendees(List<String> attendees) {
      this.attendees = attendees;
      return this;
    }

    public JobEventBuilder durationMinutes(Integer durationMinutes) {
      this.durationMinutes = durationMinutes;
      return this;
    }

    public JobEventBuilder outcome(String outcome) {
      this.outcome = outcome;
      return this;
    }

    public JobEventBuilder outcomeNotes(String outcomeNotes) {
      this.outcomeNotes = outcomeNotes;
      return this;
    }

    public JobEventBuilder reminderEnabled(Boolean reminderEnabled) {
      this.reminderEnabled = reminderEnabled;
      return this;
    }

    public JobEventBuilder reminderMinutesBefore(Integer reminderMinutesBefore) {
      this.reminderMinutesBefore = reminderMinutesBefore;
      return this;
    }

    public JobEvent build() {
      JobEvent event = new JobEvent();
      event.setId(this.id);
      event.setJobId(this.jobId);
      event.setEventType(this.eventType);
      event.setEventStatus(this.eventStatus);
      event.setEventDate(this.eventDate);
      event.setTitle(this.title);
      event.setDescription(this.description);
      event.setLocation(this.location);
      event.setAttendees(this.attendees);
      event.setDurationMinutes(this.durationMinutes);
      event.setOutcome(this.outcome);
      event.setOutcomeNotes(this.outcomeNotes);
      event.setReminderEnabled(this.reminderEnabled);
      event.setReminderMinutesBefore(this.reminderMinutesBefore);
      event.setCreatedAt(Instant.now());
      event.setUpdatedAt(Instant.now());
      return event;
    }
  }
}