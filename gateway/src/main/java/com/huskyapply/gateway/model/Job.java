package com.huskyapply.gateway.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("jobs")
public class Job {

  @Id private UUID id;

  @Column("jd_url")
  private String jdUrl;

  @Column("resume_uri")
  private String resumeUri;

  @Column("status")
  private String status;

  @Column("user_id")
  private UUID userId;

  @Column("job_title")
  private String jobTitle;

  @Column("company_name")
  private String companyName;

  @Column("created_at")
  private Instant createdAt;

  @Column("batch_job_id")
  private UUID batchJobId;

  // Job tracking fields (added for tracking functionality)
  @Column("job_description")
  private String jobDescription;

  @Column("application_deadline")
  private Instant applicationDeadline;

  @Column("expected_salary_min")
  private Integer expectedSalaryMin;

  @Column("expected_salary_max")
  private Integer expectedSalaryMax;

  @Column("job_location")
  private String jobLocation;

  @Column("application_method")
  private String applicationMethod;

  @Column("referral_contact")
  private String referralContact;

  @Column("job_priority")
  private String jobPriority;

  @Column("notes")
  private String notes;

  @Column("last_updated_at")
  private Instant lastUpdatedAt;

  public Job() {}

  public Job(
      UUID id,
      String jdUrl,
      String resumeUri,
      String status,
      UUID userId,
      String jobTitle,
      String companyName,
      Instant createdAt) {
    this.id = id;
    this.jdUrl = jdUrl;
    this.resumeUri = resumeUri;
    this.status = status;
    this.userId = userId;
    this.jobTitle = jobTitle;
    this.companyName = companyName;
    this.createdAt = createdAt;
  }

  public static JobBuilder builder() {
    return new JobBuilder();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getJdUrl() {
    return jdUrl;
  }

  public void setJdUrl(String jdUrl) {
    this.jdUrl = jdUrl;
  }

  public String getResumeUri() {
    return resumeUri;
  }

  public void setResumeUri(String resumeUri) {
    this.resumeUri = resumeUri;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public UUID getBatchJobId() {
    return batchJobId;
  }

  public void setBatchJobId(UUID batchJobId) {
    this.batchJobId = batchJobId;
  }

  // Getters and Setters for Job Tracking fields
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

  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(Instant lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Job job = (Job) o;
    return Objects.equals(id, job.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static class JobBuilder {
    private UUID id;
    private String jdUrl;
    private String resumeUri;
    private String status;
    private UUID userId;
    private String jobTitle;
    private String companyName;
    private Instant createdAt;

    public JobBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public JobBuilder jdUrl(String jdUrl) {
      this.jdUrl = jdUrl;
      return this;
    }

    public JobBuilder resumeUri(String resumeUri) {
      this.resumeUri = resumeUri;
      return this;
    }

    public JobBuilder status(String status) {
      this.status = status;
      return this;
    }

    public JobBuilder userId(UUID userId) {
      this.userId = userId;
      return this;
    }

    public JobBuilder jobTitle(String jobTitle) {
      this.jobTitle = jobTitle;
      return this;
    }

    public JobBuilder companyName(String companyName) {
      this.companyName = companyName;
      return this;
    }

    public JobBuilder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Job build() {
      return new Job(id, jdUrl, resumeUri, status, userId, jobTitle, companyName, createdAt);
    }
  }
}
