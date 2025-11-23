package com.huskyapply.gateway.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents a contact person associated with a job application. Can include recruiters, hiring
 * managers, referrals, or other relevant contacts.
 */
@Table("job_contacts")
public class JobContact {

  @Id private UUID id;

  @Column("job_id")
  private UUID jobId;

  @Column("contact_name")
  private String contactName;

  @Column("contact_email")
  private String contactEmail;

  @Column("contact_phone")
  private String contactPhone;

  @Column("contact_title")
  private String contactTitle;

  @Column("contact_company")
  private String contactCompany;

  @Column("contact_department")
  private String contactDepartment;

  @Column("relationship_type")
  private String relationshipType;

  @Column("linkedin_profile")
  private String linkedinProfile;

  @Column("last_contact_date")
  private Instant lastContactDate;

  @Column("contact_method")
  private String contactMethod;

  @Column("notes")
  private String notes;

  @Column("is_primary_contact")
  private Boolean isPrimaryContact;

  @Column("created_at")
  private Instant createdAt;

  @Column("updated_at")
  private Instant updatedAt;

  // Relationship type enumeration
  public static final class RelationshipType {
    public static final String RECRUITER = "RECRUITER";
    public static final String HIRING_MANAGER = "HIRING_MANAGER";
    public static final String REFERRAL = "REFERRAL";
    public static final String COLLEAGUE = "COLLEAGUE";
    public static final String HR = "HR";
    public static final String TECHNICAL_LEAD = "TECHNICAL_LEAD";
    public static final String TEAM_MEMBER = "TEAM_MEMBER";
    public static final String VP_DIRECTOR = "VP_DIRECTOR";
    public static final String CEO_FOUNDER = "CEO_FOUNDER";

    private RelationshipType() {} // Prevent instantiation
  }

  // Contact method enumeration
  public static final class ContactMethod {
    public static final String EMAIL = "EMAIL";
    public static final String PHONE = "PHONE";
    public static final String LINKEDIN = "LINKEDIN";
    public static final String IN_PERSON = "IN_PERSON";
    public static final String VIDEO_CALL = "VIDEO_CALL";
    public static final String TEXT_MESSAGE = "TEXT_MESSAGE";
    public static final String REFERRAL_INTRODUCTION = "REFERRAL_INTRODUCTION";

    private ContactMethod() {} // Prevent instantiation
  }

  public JobContact() {}

  public JobContact(
      UUID id, UUID jobId, String contactName, String contactEmail, String relationshipType) {
    this.id = id;
    this.jobId = jobId;
    this.contactName = contactName;
    this.contactEmail = contactEmail;
    this.relationshipType = relationshipType;
    this.isPrimaryContact = false;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public static JobContactBuilder builder() {
    return new JobContactBuilder();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JobContact that = (JobContact) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "JobContact{"
        + "id="
        + id
        + ", jobId="
        + jobId
        + ", contactName='"
        + contactName
        + '\''
        + ", contactEmail='"
        + contactEmail
        + '\''
        + ", relationshipType='"
        + relationshipType
        + '\''
        + ", isPrimaryContact="
        + isPrimaryContact
        + '}';
  }

  // Builder Pattern
  public static class JobContactBuilder {
    private UUID id;
    private UUID jobId;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String contactTitle;
    private String contactCompany;
    private String contactDepartment;
    private String relationshipType;
    private String linkedinProfile;
    private Instant lastContactDate;
    private String contactMethod;
    private String notes;
    private Boolean isPrimaryContact = false;

    public JobContactBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public JobContactBuilder jobId(UUID jobId) {
      this.jobId = jobId;
      return this;
    }

    public JobContactBuilder contactName(String contactName) {
      this.contactName = contactName;
      return this;
    }

    public JobContactBuilder contactEmail(String contactEmail) {
      this.contactEmail = contactEmail;
      return this;
    }

    public JobContactBuilder contactPhone(String contactPhone) {
      this.contactPhone = contactPhone;
      return this;
    }

    public JobContactBuilder contactTitle(String contactTitle) {
      this.contactTitle = contactTitle;
      return this;
    }

    public JobContactBuilder contactCompany(String contactCompany) {
      this.contactCompany = contactCompany;
      return this;
    }

    public JobContactBuilder contactDepartment(String contactDepartment) {
      this.contactDepartment = contactDepartment;
      return this;
    }

    public JobContactBuilder relationshipType(String relationshipType) {
      this.relationshipType = relationshipType;
      return this;
    }

    public JobContactBuilder linkedinProfile(String linkedinProfile) {
      this.linkedinProfile = linkedinProfile;
      return this;
    }

    public JobContactBuilder lastContactDate(Instant lastContactDate) {
      this.lastContactDate = lastContactDate;
      return this;
    }

    public JobContactBuilder contactMethod(String contactMethod) {
      this.contactMethod = contactMethod;
      return this;
    }

    public JobContactBuilder notes(String notes) {
      this.notes = notes;
      return this;
    }

    public JobContactBuilder isPrimaryContact(Boolean isPrimaryContact) {
      this.isPrimaryContact = isPrimaryContact;
      return this;
    }

    public JobContact build() {
      JobContact contact = new JobContact();
      contact.setId(this.id);
      contact.setJobId(this.jobId);
      contact.setContactName(this.contactName);
      contact.setContactEmail(this.contactEmail);
      contact.setContactPhone(this.contactPhone);
      contact.setContactTitle(this.contactTitle);
      contact.setContactCompany(this.contactCompany);
      contact.setContactDepartment(this.contactDepartment);
      contact.setRelationshipType(this.relationshipType);
      contact.setLinkedinProfile(this.linkedinProfile);
      contact.setLastContactDate(this.lastContactDate);
      contact.setContactMethod(this.contactMethod);
      contact.setNotes(this.notes);
      contact.setIsPrimaryContact(this.isPrimaryContact);
      contact.setCreatedAt(Instant.now());
      contact.setUpdatedAt(Instant.now());
      return contact;
    }
  }
}
