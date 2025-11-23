package com.huskyapply.gateway.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a detailed interview entity for job applications. Contains comprehensive interview
 * information including preparation, questions, and outcomes.
 */
@Table("interviews")
public class Interview {

  @Id private UUID id;

  @Column("job_id")
  private UUID jobId;

  @Column("job_event_id")
  private UUID jobEventId;

  @Column("interview_type")
  private String interviewType;

  @Column("interview_round")
  private Integer interviewRound;

  @Column("scheduled_at")
  private Instant scheduledAt;

  @Column("duration_minutes")
  private Integer durationMinutes;

  @Column("interviewer_name")
  private String interviewerName;

  @Column("interviewer_title")
  private String interviewerTitle;

  @Column("interviewer_email")
  private String interviewerEmail;

  @Column("interviewer_phone")
  private String interviewerPhone;

  @Column("location")
  private String location;

  @Column("meeting_link")
  private String meetingLink;

  @Column("preparation_notes")
  private String preparationNotes;

  @Column("interview_questions")
  private JsonNode interviewQuestions; // JSONB field for questions and answers

  @Column("technical_requirements")
  private String technicalRequirements;

  @Column("status")
  private String status;

  @Column("feedback")
  private String feedback;

  @Column("rating")
  private Integer rating; // 1-10 scale

  @Column("outcome")
  private String outcome;

  @Column("follow_up_required")
  private Boolean followUpRequired;

  @Column("follow_up_notes")
  private String followUpNotes;

  @Column("created_at")
  private Instant createdAt;

  @Column("updated_at")
  private Instant updatedAt;

  // Interview type enumeration
  public static final class InterviewType {
    public static final String PHONE = "PHONE";
    public static final String VIDEO = "VIDEO";
    public static final String IN_PERSON = "IN_PERSON";
    public static final String TECHNICAL = "TECHNICAL";
    public static final String BEHAVIORAL = "BEHAVIORAL";
    public static final String SYSTEM_DESIGN = "SYSTEM_DESIGN";
    public static final String CODING = "CODING";
    public static final String PANEL = "PANEL";
    public static final String FINAL = "FINAL";
    public static final String HR_SCREENING = "HR_SCREENING";

    private InterviewType() {} // Prevent instantiation
  }

  // Interview status enumeration
  public static final class InterviewStatus {
    public static final String SCHEDULED = "SCHEDULED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RESCHEDULED = "RESCHEDULED";
    public static final String NO_SHOW = "NO_SHOW";

    private InterviewStatus() {} // Prevent instantiation
  }

  // Interview outcome enumeration
  public static final class InterviewOutcome {
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String WAITING_FOR_FEEDBACK = "WAITING_FOR_FEEDBACK";
    public static final String STRONG_HIRE = "STRONG_HIRE";
    public static final String HIRE = "HIRE";
    public static final String NO_HIRE = "NO_HIRE";
    public static final String STRONG_NO_HIRE = "STRONG_NO_HIRE";

    private InterviewOutcome() {} // Prevent instantiation
  }

  public Interview() {}

  public Interview(
      UUID id,
      UUID jobId,
      String interviewType,
      Integer interviewRound,
      Instant scheduledAt,
      String status) {
    this.id = id;
    this.jobId = jobId;
    this.interviewType = interviewType;
    this.interviewRound = interviewRound;
    this.scheduledAt = scheduledAt;
    this.status = status;
    this.durationMinutes = 60; // Default 1 hour
    this.followUpRequired = false;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public static InterviewBuilder builder() {
    return new InterviewBuilder();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Interview interview = (Interview) o;
    return Objects.equals(id, interview.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Interview{"
        + "id="
        + id
        + ", jobId="
        + jobId
        + ", interviewType='"
        + interviewType
        + '\''
        + ", interviewRound="
        + interviewRound
        + ", scheduledAt="
        + scheduledAt
        + ", status='"
        + status
        + '\''
        + ", interviewerName='"
        + interviewerName
        + '\''
        + '}';
  }

  // Builder Pattern
  public static class InterviewBuilder {
    private UUID id;
    private UUID jobId;
    private UUID jobEventId;
    private String interviewType;
    private Integer interviewRound = 1;
    private Instant scheduledAt;
    private Integer durationMinutes = 60;
    private String interviewerName;
    private String interviewerTitle;
    private String interviewerEmail;
    private String interviewerPhone;
    private String location;
    private String meetingLink;
    private String preparationNotes;
    private JsonNode interviewQuestions;
    private String technicalRequirements;
    private String status = InterviewStatus.SCHEDULED;
    private String feedback;
    private Integer rating;
    private String outcome;
    private Boolean followUpRequired = false;
    private String followUpNotes;

    public InterviewBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public InterviewBuilder jobId(UUID jobId) {
      this.jobId = jobId;
      return this;
    }

    public InterviewBuilder jobEventId(UUID jobEventId) {
      this.jobEventId = jobEventId;
      return this;
    }

    public InterviewBuilder interviewType(String interviewType) {
      this.interviewType = interviewType;
      return this;
    }

    public InterviewBuilder interviewRound(Integer interviewRound) {
      this.interviewRound = interviewRound;
      return this;
    }

    public InterviewBuilder scheduledAt(Instant scheduledAt) {
      this.scheduledAt = scheduledAt;
      return this;
    }

    public InterviewBuilder durationMinutes(Integer durationMinutes) {
      this.durationMinutes = durationMinutes;
      return this;
    }

    public InterviewBuilder interviewerName(String interviewerName) {
      this.interviewerName = interviewerName;
      return this;
    }

    public InterviewBuilder interviewerTitle(String interviewerTitle) {
      this.interviewerTitle = interviewerTitle;
      return this;
    }

    public InterviewBuilder interviewerEmail(String interviewerEmail) {
      this.interviewerEmail = interviewerEmail;
      return this;
    }

    public InterviewBuilder interviewerPhone(String interviewerPhone) {
      this.interviewerPhone = interviewerPhone;
      return this;
    }

    public InterviewBuilder location(String location) {
      this.location = location;
      return this;
    }

    public InterviewBuilder meetingLink(String meetingLink) {
      this.meetingLink = meetingLink;
      return this;
    }

    public InterviewBuilder preparationNotes(String preparationNotes) {
      this.preparationNotes = preparationNotes;
      return this;
    }

    public InterviewBuilder interviewQuestions(JsonNode interviewQuestions) {
      this.interviewQuestions = interviewQuestions;
      return this;
    }

    public InterviewBuilder technicalRequirements(String technicalRequirements) {
      this.technicalRequirements = technicalRequirements;
      return this;
    }

    public InterviewBuilder status(String status) {
      this.status = status;
      return this;
    }

    public InterviewBuilder feedback(String feedback) {
      this.feedback = feedback;
      return this;
    }

    public InterviewBuilder rating(Integer rating) {
      this.rating = rating;
      return this;
    }

    public InterviewBuilder outcome(String outcome) {
      this.outcome = outcome;
      return this;
    }

    public InterviewBuilder followUpRequired(Boolean followUpRequired) {
      this.followUpRequired = followUpRequired;
      return this;
    }

    public InterviewBuilder followUpNotes(String followUpNotes) {
      this.followUpNotes = followUpNotes;
      return this;
    }

    public Interview build() {
      Interview interview = new Interview();
      interview.setId(this.id);
      interview.setJobId(this.jobId);
      interview.setJobEventId(this.jobEventId);
      interview.setInterviewType(this.interviewType);
      interview.setInterviewRound(this.interviewRound);
      interview.setScheduledAt(this.scheduledAt);
      interview.setDurationMinutes(this.durationMinutes);
      interview.setInterviewerName(this.interviewerName);
      interview.setInterviewerTitle(this.interviewerTitle);
      interview.setInterviewerEmail(this.interviewerEmail);
      interview.setInterviewerPhone(this.interviewerPhone);
      interview.setLocation(this.location);
      interview.setMeetingLink(this.meetingLink);
      interview.setPreparationNotes(this.preparationNotes);
      interview.setInterviewQuestions(this.interviewQuestions);
      interview.setTechnicalRequirements(this.technicalRequirements);
      interview.setStatus(this.status);
      interview.setFeedback(this.feedback);
      interview.setRating(this.rating);
      interview.setOutcome(this.outcome);
      interview.setFollowUpRequired(this.followUpRequired);
      interview.setFollowUpNotes(this.followUpNotes);
      interview.setCreatedAt(Instant.now());
      interview.setUpdatedAt(Instant.now());
      return interview;
    }
  }
}
