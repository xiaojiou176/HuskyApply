package com.huskyapply.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@jakarta.persistence.Table(name = "jobs")
public class Job {

  @Id
  @jakarta.persistence.Column(name = "id")
  private UUID id;

  @jakarta.persistence.Column(name = "jd_url")
  private String jdUrl;

  @jakarta.persistence.Column(name = "resume_uri")
  private String resumeUri;

  @jakarta.persistence.Column(name = "status")
  private String status;

  @jakarta.persistence.Column(name = "user_id")
  private UUID userId;

  @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @jakarta.persistence.JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @jakarta.persistence.Column(name = "job_title")
  private String jobTitle;

  @jakarta.persistence.Column(name = "company_name")
  private String companyName;

  @jakarta.persistence.Column(name = "created_at")
  private Instant createdAt;

  @jakarta.persistence.Column(name = "batch_job_id")
  private UUID batchJobId;

  @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @jakarta.persistence.JoinColumn(name = "batch_job_id", insertable = false, updatable = false)
  private BatchJob batchJob;

  @jakarta.persistence.OneToMany(
      mappedBy = "job",
      fetch = jakarta.persistence.FetchType.LAZY,
      cascade = jakarta.persistence.CascadeType.ALL)
  private java.util.List<Artifact> artifacts;

  // Job tracking fields (added for tracking functionality)
  @jakarta.persistence.Column(name = "job_description")
  private String jobDescription;

  @jakarta.persistence.Column(name = "application_deadline")
  private Instant applicationDeadline;

  @jakarta.persistence.Column(name = "expected_salary_min")
  private Integer expectedSalaryMin;

  @jakarta.persistence.Column(name = "expected_salary_max")
  private Integer expectedSalaryMax;

  @jakarta.persistence.Column(name = "job_location")
  private String jobLocation;

  @jakarta.persistence.Column(name = "application_method")
  private String applicationMethod;

  @jakarta.persistence.Column(name = "referral_contact")
  private String referralContact;

  @jakarta.persistence.Column(name = "job_priority")
  private String jobPriority;

  @jakarta.persistence.Column(name = "notes")
  private String notes;

  @jakarta.persistence.Column(name = "last_updated_at")
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

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
    if (user != null) {
      this.userId = user.getId();
    }
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

  public BatchJob getBatchJob() {
    return batchJob;
  }

  public void setBatchJob(BatchJob batchJob) {
    this.batchJob = batchJob;
    if (batchJob != null) {
      this.batchJobId = batchJob.getId();
    }
  }

  public java.util.List<Artifact> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(java.util.List<Artifact> artifacts) {
    this.artifacts = artifacts;
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

    public JobBuilder user(User user) {
      this.userId = user.getId();
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
