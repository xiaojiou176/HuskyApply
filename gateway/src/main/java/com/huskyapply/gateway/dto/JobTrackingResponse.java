package com.huskyapply.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for job tracking information.
 * Contains comprehensive job details, timeline, contacts, and progress indicators.
 */
public class JobTrackingResponse {

  @JsonProperty("job_id")
  private UUID jobId;

  @JsonProperty("job_title")
  private String jobTitle;

  @JsonProperty("company_name")
  private String companyName;

  @JsonProperty("job_description")
  private String jobDescription;

  @JsonProperty("status")
  private String status;

  @JsonProperty("job_priority")
  private String jobPriority;

  @JsonProperty("application_deadline")
  private Instant applicationDeadline;

  @JsonProperty("expected_salary_min")
  private Integer expectedSalaryMin;

  @JsonProperty("expected_salary_max")
  private Integer expectedSalaryMax;

  @JsonProperty("job_location")
  private String jobLocation;

  @JsonProperty("application_method")
  private String applicationMethod;

  @JsonProperty("referral_contact")
  private String referralContact;

  @JsonProperty("notes")
  private String notes;

  @JsonProperty("created_at")
  private Instant createdAt;

  @JsonProperty("last_updated_at")
  private Instant lastUpdatedAt;

  @JsonProperty("timeline")
  private List<JobEventResponse> timeline;

  @JsonProperty("interviews")
  private List<InterviewResponse> interviews;

  @JsonProperty("contacts")
  private List<JobContactResponse> contacts;

  @JsonProperty("progress_indicators")
  private ProgressIndicators progressIndicators;

  @JsonProperty("statistics")
  private JobStatistics statistics;

  @JsonProperty("upcoming_events")
  private List<JobEventResponse> upcomingEvents;

  @JsonProperty("next_action_items")
  private List<ActionItem> nextActionItems;

  public JobTrackingResponse() {}

  public JobTrackingResponse(
      UUID jobId,
      String jobTitle,
      String companyName,
      String status,
      String jobPriority,
      Instant createdAt) {
    this.jobId = jobId;
    this.jobTitle = jobTitle;
    this.companyName = companyName;
    this.status = status;
    this.jobPriority = jobPriority;
    this.createdAt = createdAt;
  }

  // Getters and Setters
  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public void setJobTitle(String jobTitle) {
    this.jobTitle = jobTitle;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public String getJobDescription() {
    return jobDescription;
  }

  public void setJobDescription(String jobDescription) {
    this.jobDescription = jobDescription;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getJobPriority() {
    return jobPriority;
  }

  public void setJobPriority(String jobPriority) {
    this.jobPriority = jobPriority;
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

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(Instant lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  public List<JobEventResponse> getTimeline() {
    return timeline;
  }

  public void setTimeline(List<JobEventResponse> timeline) {
    this.timeline = timeline;
  }

  public List<InterviewResponse> getInterviews() {
    return interviews;
  }

  public void setInterviews(List<InterviewResponse> interviews) {
    this.interviews = interviews;
  }

  public List<JobContactResponse> getContacts() {
    return contacts;
  }

  public void setContacts(List<JobContactResponse> contacts) {
    this.contacts = contacts;
  }

  public ProgressIndicators getProgressIndicators() {
    return progressIndicators;
  }

  public void setProgressIndicators(ProgressIndicators progressIndicators) {
    this.progressIndicators = progressIndicators;
  }

  public JobStatistics getStatistics() {
    return statistics;
  }

  public void setStatistics(JobStatistics statistics) {
    this.statistics = statistics;
  }

  public List<JobEventResponse> getUpcomingEvents() {
    return upcomingEvents;
  }

  public void setUpcomingEvents(List<JobEventResponse> upcomingEvents) {
    this.upcomingEvents = upcomingEvents;
  }

  public List<ActionItem> getNextActionItems() {
    return nextActionItems;
  }

  public void setNextActionItems(List<ActionItem> nextActionItems) {
    this.nextActionItems = nextActionItems;
  }

  /**
   * Progress indicators for visual dashboard display.
   */
  public static class ProgressIndicators {

    @JsonProperty("application_status")
    private String applicationStatus; // 'NOT_APPLIED', 'APPLIED', 'UNDER_REVIEW', 'INTERVIEWING', 'OFFER', 'REJECTED'

    @JsonProperty("completion_percentage")
    private Integer completionPercentage;

    @JsonProperty("days_since_application")
    private Integer daysSinceApplication;

    @JsonProperty("days_to_deadline")
    private Integer daysToDeadline;

    @JsonProperty("interview_progress")
    private String interviewProgress; // 'NOT_STARTED', 'PHONE_SCREEN', 'TECHNICAL', 'FINAL', 'COMPLETED'

    @JsonProperty("has_upcoming_events")
    private Boolean hasUpcomingEvents;

    @JsonProperty("requires_attention")
    private Boolean requiresAttention;

    @JsonProperty("attention_reason")
    private String attentionReason;

    // Getters and Setters
    public String getApplicationStatus() {
      return applicationStatus;
    }

    public void setApplicationStatus(String applicationStatus) {
      this.applicationStatus = applicationStatus;
    }

    public Integer getCompletionPercentage() {
      return completionPercentage;
    }

    public void setCompletionPercentage(Integer completionPercentage) {
      this.completionPercentage = completionPercentage;
    }

    public Integer getDaysSinceApplication() {
      return daysSinceApplication;
    }

    public void setDaysSinceApplication(Integer daysSinceApplication) {
      this.daysSinceApplication = daysSinceApplication;
    }

    public Integer getDaysToDeadline() {
      return daysToDeadline;
    }

    public void setDaysToDeadline(Integer daysToDeadline) {
      this.daysToDeadline = daysToDeadline;
    }

    public String getInterviewProgress() {
      return interviewProgress;
    }

    public void setInterviewProgress(String interviewProgress) {
      this.interviewProgress = interviewProgress;
    }

    public Boolean getHasUpcomingEvents() {
      return hasUpcomingEvents;
    }

    public void setHasUpcomingEvents(Boolean hasUpcomingEvents) {
      this.hasUpcomingEvents = hasUpcomingEvents;
    }

    public Boolean getRequiresAttention() {
      return requiresAttention;
    }

    public void setRequiresAttention(Boolean requiresAttention) {
      this.requiresAttention = requiresAttention;
    }

    public String getAttentionReason() {
      return attentionReason;
    }

    public void setAttentionReason(String attentionReason) {
      this.attentionReason = attentionReason;
    }
  }

  /**
   * Statistical information about the job application.
   */
  public static class JobStatistics {

    @JsonProperty("total_events")
    private Integer totalEvents;

    @JsonProperty("completed_events")
    private Integer completedEvents;

    @JsonProperty("pending_events")
    private Integer pendingEvents;

    @JsonProperty("total_interviews")
    private Integer totalInterviews;

    @JsonProperty("completed_interviews")
    private Integer completedInterviews;

    @JsonProperty("scheduled_interviews")
    private Integer scheduledInterviews;

    @JsonProperty("total_contacts")
    private Integer totalContacts;

    @JsonProperty("primary_contacts")
    private Integer primaryContacts;

    @JsonProperty("last_activity_date")
    private Instant lastActivityDate;

    // Getters and Setters
    public Integer getTotalEvents() {
      return totalEvents;
    }

    public void setTotalEvents(Integer totalEvents) {
      this.totalEvents = totalEvents;
    }

    public Integer getCompletedEvents() {
      return completedEvents;
    }

    public void setCompletedEvents(Integer completedEvents) {
      this.completedEvents = completedEvents;
    }

    public Integer getPendingEvents() {
      return pendingEvents;
    }

    public void setPendingEvents(Integer pendingEvents) {
      this.pendingEvents = pendingEvents;
    }

    public Integer getTotalInterviews() {
      return totalInterviews;
    }

    public void setTotalInterviews(Integer totalInterviews) {
      this.totalInterviews = totalInterviews;
    }

    public Integer getCompletedInterviews() {
      return completedInterviews;
    }

    public void setCompletedInterviews(Integer completedInterviews) {
      this.completedInterviews = completedInterviews;
    }

    public Integer getScheduledInterviews() {
      return scheduledInterviews;
    }

    public void setScheduledInterviews(Integer scheduledInterviews) {
      this.scheduledInterviews = scheduledInterviews;
    }

    public Integer getTotalContacts() {
      return totalContacts;
    }

    public void setTotalContacts(Integer totalContacts) {
      this.totalContacts = totalContacts;
    }

    public Integer getPrimaryContacts() {
      return primaryContacts;
    }

    public void setPrimaryContacts(Integer primaryContacts) {
      this.primaryContacts = primaryContacts;
    }

    public Instant getLastActivityDate() {
      return lastActivityDate;
    }

    public void setLastActivityDate(Instant lastActivityDate) {
      this.lastActivityDate = lastActivityDate;
    }
  }

  /**
   * Represents a recommended action item for the user.
   */
  public static class ActionItem {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type; // 'FOLLOW_UP', 'INTERVIEW_PREP', 'DEADLINE_REMINDER', 'CONTACT', 'APPLICATION_SUBMIT'

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("priority")
    private String priority; // 'HIGH', 'MEDIUM', 'LOW'

    @JsonProperty("due_date")
    private Instant dueDate;

    @JsonProperty("estimated_minutes")
    private Integer estimatedMinutes;

    @JsonProperty("related_entity_id")
    private UUID relatedEntityId; // Could be event_id, interview_id, contact_id

    @JsonProperty("related_entity_type")
    private String relatedEntityType; // 'EVENT', 'INTERVIEW', 'CONTACT'

    public ActionItem() {}

    public ActionItem(String type, String title, String description, String priority) {
      this.type = type;
      this.title = title;
      this.description = description;
      this.priority = priority;
    }

    // Getters and Setters
    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
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

    public String getPriority() {
      return priority;
    }

    public void setPriority(String priority) {
      this.priority = priority;
    }

    public Instant getDueDate() {
      return dueDate;
    }

    public void setDueDate(Instant dueDate) {
      this.dueDate = dueDate;
    }

    public Integer getEstimatedMinutes() {
      return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
      this.estimatedMinutes = estimatedMinutes;
    }

    public UUID getRelatedEntityId() {
      return relatedEntityId;
    }

    public void setRelatedEntityId(UUID relatedEntityId) {
      this.relatedEntityId = relatedEntityId;
    }

    public String getRelatedEntityType() {
      return relatedEntityType;
    }

    public void setRelatedEntityType(String relatedEntityType) {
      this.relatedEntityType = relatedEntityType;
    }
  }
}