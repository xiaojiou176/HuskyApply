package com.huskyapply.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for job contact information. Contains comprehensive contact details and interaction history.
 */
public class JobContactResponse {

  @JsonProperty("contact_id")
  private UUID contactId;

  @JsonProperty("job_id")
  private UUID jobId;

  @JsonProperty("contact_name")
  private String contactName;

  @JsonProperty("contact_email")
  private String contactEmail;

  @JsonProperty("contact_phone")
  private String contactPhone;

  @JsonProperty("contact_title")
  private String contactTitle;

  @JsonProperty("contact_company")
  private String contactCompany;

  @JsonProperty("contact_department")
  private String contactDepartment;

  @JsonProperty("relationship_type")
  private String relationshipType;

  @JsonProperty("linkedin_profile")
  private String linkedinProfile;

  @JsonProperty("last_contact_date")
  private Instant lastContactDate;

  @JsonProperty("contact_method")
  private String contactMethod;

  @JsonProperty("notes")
  private String notes;

  @JsonProperty("is_primary_contact")
  private Boolean isPrimaryContact;

  @JsonProperty("created_at")
  private Instant createdAt;

  @JsonProperty("updated_at")
  private Instant updatedAt;

  @JsonProperty("days_since_last_contact")
  private Integer daysSinceLastContact;

  @JsonProperty("contact_frequency_score")
  private String contactFrequencyScore; // 'HIGH', 'MEDIUM', 'LOW', 'STALE'

  @JsonProperty("should_follow_up")
  private Boolean shouldFollowUp;

  @JsonProperty("follow_up_priority")
  private String followUpPriority; // 'HIGH', 'MEDIUM', 'LOW'

  public JobContactResponse() {}

  public JobContactResponse(
      UUID contactId,
      UUID jobId,
      String contactName,
      String contactEmail,
      String relationshipType,
      Boolean isPrimaryContact) {
    this.contactId = contactId;
    this.jobId = jobId;
    this.contactName = contactName;
    this.contactEmail = contactEmail;
    this.relationshipType = relationshipType;
    this.isPrimaryContact = isPrimaryContact;
  }

  // Getters and Setters
  public UUID getContactId() {
    return contactId;
  }

  public void setContactId(UUID contactId) {
    this.contactId = contactId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

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

  public String getContactCompany() {
    return contactCompany;
  }

  public void setContactCompany(String contactCompany) {
    this.contactCompany = contactCompany;
  }

  public String getContactDepartment() {
    return contactDepartment;
  }

  public void setContactDepartment(String contactDepartment) {
    this.contactDepartment = contactDepartment;
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

  public Instant getLastContactDate() {
    return lastContactDate;
  }

  public void setLastContactDate(Instant lastContactDate) {
    this.lastContactDate = lastContactDate;
  }

  public String getContactMethod() {
    return contactMethod;
  }

  public void setContactMethod(String contactMethod) {
    this.contactMethod = contactMethod;
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

  public Integer getDaysSinceLastContact() {
    return daysSinceLastContact;
  }

  public void setDaysSinceLastContact(Integer daysSinceLastContact) {
    this.daysSinceLastContact = daysSinceLastContact;
  }

  public String getContactFrequencyScore() {
    return contactFrequencyScore;
  }

  public void setContactFrequencyScore(String contactFrequencyScore) {
    this.contactFrequencyScore = contactFrequencyScore;
  }

  public Boolean getShouldFollowUp() {
    return shouldFollowUp;
  }

  public void setShouldFollowUp(Boolean shouldFollowUp) {
    this.shouldFollowUp = shouldFollowUp;
  }

  public String getFollowUpPriority() {
    return followUpPriority;
  }

  public void setFollowUpPriority(String followUpPriority) {
    this.followUpPriority = followUpPriority;
  }

  @Override
  public String toString() {
    return "JobContactResponse{"
        + "contactId=" + contactId
        + ", contactName='" + contactName + '\''
        + ", contactEmail='" + contactEmail + '\''
        + ", relationshipType='" + relationshipType + '\''
        + ", isPrimaryContact=" + isPrimaryContact
        + '}';
  }
}