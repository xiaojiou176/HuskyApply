package com.huskyapply.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for interview information. Contains comprehensive interview details including preparation notes, outcomes, and follow-up requirements.
 */
public class InterviewResponse {

  @JsonProperty("interview_id")
  private UUID interviewId;

  @JsonProperty("job_id")
  private UUID jobId;

  @JsonProperty("job_event_id")
  private UUID jobEventId;

  @JsonProperty("interview_type")
  private String interviewType;

  @JsonProperty("interview_round")
  private Integer interviewRound;

  @JsonProperty("scheduled_at")
  private Instant scheduledAt;

  @JsonProperty("duration_minutes")
  private Integer durationMinutes;

  @JsonProperty("interviewer_name")
  private String interviewerName;

  @JsonProperty("interviewer_title")
  private String interviewerTitle;

  @JsonProperty("interviewer_email")
  private String interviewerEmail;

  @JsonProperty("interviewer_phone")
  private String interviewerPhone;

  @JsonProperty("location")
  private String location;

  @JsonProperty("meeting_link")
  private String meetingLink;

  @JsonProperty("preparation_notes")
  private String preparationNotes;

  @JsonProperty("interview_questions")
  private JsonNode interviewQuestions;

  @JsonProperty("technical_requirements")
  private String technicalRequirements;

  @JsonProperty("status")
  private String status;

  @JsonProperty("feedback")
  private String feedback;

  @JsonProperty("rating")
  private Integer rating;

  @JsonProperty("outcome")
  private String outcome;

  @JsonProperty("follow_up_required")
  private Boolean followUpRequired;

  @JsonProperty("follow_up_notes")
  private String followUpNotes;

  @JsonProperty("created_at")
  private Instant createdAt;

  @JsonProperty("updated_at")
  private Instant updatedAt;

  @JsonProperty("is_upcoming")
  private Boolean isUpcoming;

  @JsonProperty("time_until_interview")
  private String timeUntilInterview; // Human readable: "2 hours", "3 days", etc.

  @JsonProperty("preparation_status")
  private String preparationStatus; // 'NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'

  public InterviewResponse() {}

  public InterviewResponse(
      UUID interviewId,
      UUID jobId,
      String interviewType,
      Integer interviewRound,
      Instant scheduledAt,
      String status) {
    this.interviewId = interviewId;
    this.jobId = jobId;
    this.interviewType = interviewType;
    this.interviewRound = interviewRound;
    this.scheduledAt = scheduledAt;
    this.status = status;
  }

  // Getters and Setters
  public UUID getInterviewId() {
    return interviewId;
  }

  public void setInterviewId(UUID interviewId) {
    this.interviewId = interviewId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public UUID getJobEventId() {
    return jobEventId;
  }

  public void setJobEventId(UUID jobEventId) {
    this.jobEventId = jobEventId;
  }

  public String getInterviewType() {
    return interviewType;
  }

  public void setInterviewType(String interviewType) {
    this.interviewType = interviewType;
  }

  public Integer getInterviewRound() {
    return interviewRound;
  }

  public void setInterviewRound(Integer interviewRound) {
    this.interviewRound = interviewRound;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public void setScheduledAt(Instant scheduledAt) {
    this.scheduledAt = scheduledAt;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public String getInterviewerName() {
    return interviewerName;
  }

  public void setInterviewerName(String interviewerName) {
    this.interviewerName = interviewerName;
  }

  public String getInterviewerTitle() {
    return interviewerTitle;
  }

  public void setInterviewerTitle(String interviewerTitle) {
    this.interviewerTitle = interviewerTitle;
  }

  public String getInterviewerEmail() {
    return interviewerEmail;
  }

  public void setInterviewerEmail(String interviewerEmail) {
    this.interviewerEmail = interviewerEmail;
  }

  public String getInterviewerPhone() {
    return interviewerPhone;
  }

  public void setInterviewerPhone(String interviewerPhone) {
    this.interviewerPhone = interviewerPhone;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getMeetingLink() {
    return meetingLink;
  }

  public void setMeetingLink(String meetingLink) {
    this.meetingLink = meetingLink;
  }

  public String getPreparationNotes() {
    return preparationNotes;
  }

  public void setPreparationNotes(String preparationNotes) {
    this.preparationNotes = preparationNotes;
  }

  public JsonNode getInterviewQuestions() {
    return interviewQuestions;
  }

  public void setInterviewQuestions(JsonNode interviewQuestions) {
    this.interviewQuestions = interviewQuestions;
  }

  public String getTechnicalRequirements() {
    return technicalRequirements;
  }

  public void setTechnicalRequirements(String technicalRequirements) {
    this.technicalRequirements = technicalRequirements;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getFeedback() {
    return feedback;
  }

  public void setFeedback(String feedback) {
    this.feedback = feedback;
  }

  public Integer getRating() {
    return rating;
  }

  public void setRating(Integer rating) {
    this.rating = rating;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public Boolean getFollowUpRequired() {
    return followUpRequired;
  }

  public void setFollowUpRequired(Boolean followUpRequired) {
    this.followUpRequired = followUpRequired;
  }

  public String getFollowUpNotes() {
    return followUpNotes;
  }

  public void setFollowUpNotes(String followUpNotes) {
    this.followUpNotes = followUpNotes;
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

  public Boolean getIsUpcoming() {
    return isUpcoming;
  }

  public void setIsUpcoming(Boolean isUpcoming) {
    this.isUpcoming = isUpcoming;
  }

  public String getTimeUntilInterview() {
    return timeUntilInterview;
  }

  public void setTimeUntilInterview(String timeUntilInterview) {
    this.timeUntilInterview = timeUntilInterview;
  }

  public String getPreparationStatus() {
    return preparationStatus;
  }

  public void setPreparationStatus(String preparationStatus) {
    this.preparationStatus = preparationStatus;
  }

  @Override
  public String toString() {
    return "InterviewResponse{"
        + "interviewId=" + interviewId
        + ", interviewType='" + interviewType + '\''
        + ", interviewRound=" + interviewRound
        + ", scheduledAt=" + scheduledAt
        + ", status='" + status + '\''
        + ", interviewerName='" + interviewerName + '\''
        + '}';
  }
}