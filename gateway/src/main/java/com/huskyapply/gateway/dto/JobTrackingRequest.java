package com.huskyapply.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Request DTO for creating or updating job tracking information. Includes basic job details,
 * priority, deadlines, and application notes.
 */
public class JobTrackingRequest {

  @JsonProperty("job_description")
  @Size(max = 5000, message = "Job description must not exceed 5000 characters")
  private String jobDescription;

  @JsonProperty("application_deadline")
  private Instant applicationDeadline;

  @JsonProperty("expected_salary_min")
  private Integer expectedSalaryMin;

  @JsonProperty("expected_salary_max")
  private Integer expectedSalaryMax;

  @JsonProperty("job_location")
  @Size(max = 255, message = "Job location must not exceed 255 characters")
  private String jobLocation;

  @JsonProperty("application_method")
  @Size(max = 100, message = "Application method must not exceed 100 characters")
  private String applicationMethod; // 'online', 'email', 'referral', 'in_person'

  @JsonProperty("referral_contact")
  @Size(max = 255, message = "Referral contact must not exceed 255 characters")
  private String referralContact;

  @JsonProperty("job_priority")
  @NotBlank(message = "Job priority is required")
  private String jobPriority; // 'HIGH', 'MEDIUM', 'LOW'

  @JsonProperty("notes")
  @Size(max = 5000, message = "Notes must not exceed 5000 characters")
  private String notes;

  @JsonProperty("contacts")
  private List<JobContactRequest> contacts;

  @JsonProperty("events")
  private List<JobEventRequest> events;

  public JobTrackingRequest() {}

  public JobTrackingRequest(
      String jobDescription, Instant applicationDeadline, String jobPriority, String notes) {
    this.jobDescription = jobDescription;
    this.applicationDeadline = applicationDeadline;
    this.jobPriority = jobPriority;
    this.notes = notes;
  }

  // Getters and Setters
  public String getJobDescription() {
    return jobDescription;
  }

  public void setJobDescription(String jobDescription) {
    this.jobDescription = jobDescription;
  }

  public Instant getApplicationDeadline() {
    return applicationDeadline;
  }

  public void setApplicationDeadline(Instant applicationDeadline) {
    this.applicationDeadline = applicationDeadline;
  }

  public Integer getExpectedSalaryMin() {
    return expectedSalaryMin;
  }

  public void setExpectedSalaryMin(Integer expectedSalaryMin) {
    this.expectedSalaryMin = expectedSalaryMin;
  }

  public Integer getExpectedSalaryMax() {
    return expectedSalaryMax;
  }

  public void setExpectedSalaryMax(Integer expectedSalaryMax) {
    this.expectedSalaryMax = expectedSalaryMax;
  }

  public String getJobLocation() {
    return jobLocation;
  }

  public void setJobLocation(String jobLocation) {
    this.jobLocation = jobLocation;
  }

  public String getApplicationMethod() {
    return applicationMethod;
  }

  public void setApplicationMethod(String applicationMethod) {
    this.applicationMethod = applicationMethod;
  }

  public String getReferralContact() {
    return referralContact;
  }

  public void setReferralContact(String referralContact) {
    this.referralContact = referralContact;
  }

  public String getJobPriority() {
    return jobPriority;
  }

  public void setJobPriority(String jobPriority) {
    this.jobPriority = jobPriority;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public List<JobContactRequest> getContacts() {
    return contacts;
  }

  public void setContacts(List<JobContactRequest> contacts) {
    this.contacts = contacts;
  }

  public List<JobEventRequest> getEvents() {
    return events;
  }

  public void setEvents(List<JobEventRequest> events) {
    this.events = events;
  }

  @Override
  public String toString() {
    return "JobTrackingRequest{"
        + "jobPriority='"
        + jobPriority
        + '\''
        + ", applicationDeadline="
        + applicationDeadline
        + ", jobLocation='"
        + jobLocation
        + '\''
        + ", applicationMethod='"
        + applicationMethod
        + '\''
        + '}';
  }

  /** Nested DTO for job contact information within tracking request. */
  public static class JobContactRequest {

    @JsonProperty("contact_name")
    @NotBlank(message = "Contact name is required")
    @Size(max = 255, message = "Contact name must not exceed 255 characters")
    private String contactName;

    @JsonProperty("contact_email")
    @Size(max = 255, message = "Contact email must not exceed 255 characters")
    private String contactEmail;

    @JsonProperty("contact_phone")
    @Size(max = 50, message = "Contact phone must not exceed 50 characters")
    private String contactPhone;

    @JsonProperty("contact_title")
    @Size(max = 255, message = "Contact title must not exceed 255 characters")
    private String contactTitle;

    @JsonProperty("relationship_type")
    @NotBlank(message = "Relationship type is required")
    private String relationshipType;

    @JsonProperty("linkedin_profile")
    @Size(max = 500, message = "LinkedIn profile must not exceed 500 characters")
    private String linkedinProfile;

    @JsonProperty("notes")
    @Size(max = 2000, message = "Contact notes must not exceed 2000 characters")
    private String notes;

    @JsonProperty("is_primary_contact")
    private Boolean isPrimaryContact = false;

    // Getters and Setters
    public String getContactName() {
      return contactName;
    }

    public void setContactName(String contactName) {
      this.contactName = contactName;
    }

    public String getContactEmail() {
      return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
      this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
      return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
      this.contactPhone = contactPhone;
    }

    public String getContactTitle() {
      return contactTitle;
    }

    public void setContactTitle(String contactTitle) {
      this.contactTitle = contactTitle;
    }

    public String getRelationshipType() {
      return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
      this.relationshipType = relationshipType;
    }

    public String getLinkedinProfile() {
      return linkedinProfile;
    }

    public void setLinkedinProfile(String linkedinProfile) {
      this.linkedinProfile = linkedinProfile;
    }

    public String getNotes() {
      return notes;
    }

    public void setNotes(String notes) {
      this.notes = notes;
    }

    public Boolean getIsPrimaryContact() {
      return isPrimaryContact;
    }

    public void setIsPrimaryContact(Boolean isPrimaryContact) {
      this.isPrimaryContact = isPrimaryContact;
    }
  }

  /** Nested DTO for job event information within tracking request. */
  public static class JobEventRequest {

    @JsonProperty("event_type")
    @NotBlank(message = "Event type is required")
    private String eventType;

    @JsonProperty("event_date")
    @NotNull(message = "Event date is required")
    private Instant eventDate;

    @JsonProperty("title")
    @NotBlank(message = "Event title is required")
    @Size(max = 255, message = "Event title must not exceed 255 characters")
    private String title;

    @JsonProperty("description")
    @Size(max = 2000, message = "Event description must not exceed 2000 characters")
    private String description;

    @JsonProperty("location")
    @Size(max = 255, message = "Event location must not exceed 255 characters")
    private String location;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("reminder_enabled")
    private Boolean reminderEnabled = true;

    @JsonProperty("reminder_minutes_before")
    private Integer reminderMinutesBefore = 60;

    // Getters and Setters
    public String getEventType() {
      return eventType;
    }

    public void setEventType(String eventType) {
      this.eventType = eventType;
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

    public Integer getDurationMinutes() {
      return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
      this.durationMinutes = durationMinutes;
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
  }
}
