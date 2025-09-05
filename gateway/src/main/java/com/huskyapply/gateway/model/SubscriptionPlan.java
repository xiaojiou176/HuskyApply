package com.huskyapply.gateway.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "name", nullable = false, unique = true, length = 100)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "price_monthly", nullable = false, precision = 10, scale = 2)
  private BigDecimal priceMonthly;

  @Column(name = "price_yearly", precision = 10, scale = 2)
  private BigDecimal priceYearly;

  @Column(name = "jobs_per_month")
  private Integer jobsPerMonth;

  @Column(name = "templates_limit")
  private Integer templatesLimit;

  @Column(name = "batch_jobs_limit")
  private Integer batchJobsLimit;

  @Column(name = "ai_models_access", columnDefinition = "TEXT")
  private String aiModelsAccess; // JSON array of allowed models

  @Column(name = "priority_processing")
  private Boolean priorityProcessing = false;

  @Column(name = "api_access")
  private Boolean apiAccess = false;

  @Column(name = "team_collaboration")
  private Boolean teamCollaboration = false;

  @Column(name = "white_label")
  private Boolean whiteLabel = false;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "stripe_price_id_monthly", length = 100)
  private String stripePriceIdMonthly;

  @Column(name = "stripe_price_id_yearly", length = 100)
  private String stripePriceIdYearly;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "subscriptionPlan", fetch = FetchType.LAZY)
  private List<UserSubscription> userSubscriptions = new ArrayList<>();

  public SubscriptionPlan() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getPriceMonthly() {
    return priceMonthly;
  }

  public void setPriceMonthly(BigDecimal priceMonthly) {
    this.priceMonthly = priceMonthly;
  }

  public BigDecimal getPriceYearly() {
    return priceYearly;
  }

  public void setPriceYearly(BigDecimal priceYearly) {
    this.priceYearly = priceYearly;
  }

  public Integer getJobsPerMonth() {
    return jobsPerMonth;
  }

  public void setJobsPerMonth(Integer jobsPerMonth) {
    this.jobsPerMonth = jobsPerMonth;
  }

  public Integer getTemplatesLimit() {
    return templatesLimit;
  }

  public void setTemplatesLimit(Integer templatesLimit) {
    this.templatesLimit = templatesLimit;
  }

  public Integer getBatchJobsLimit() {
    return batchJobsLimit;
  }

  public void setBatchJobsLimit(Integer batchJobsLimit) {
    this.batchJobsLimit = batchJobsLimit;
  }

  public String getAiModelsAccess() {
    return aiModelsAccess;
  }

  public void setAiModelsAccess(String aiModelsAccess) {
    this.aiModelsAccess = aiModelsAccess;
  }

  public Boolean getPriorityProcessing() {
    return priorityProcessing;
  }

  public void setPriorityProcessing(Boolean priorityProcessing) {
    this.priorityProcessing = priorityProcessing;
  }

  public Boolean getApiAccess() {
    return apiAccess;
  }

  public void setApiAccess(Boolean apiAccess) {
    this.apiAccess = apiAccess;
  }

  public Boolean getTeamCollaboration() {
    return teamCollaboration;
  }

  public void setTeamCollaboration(Boolean teamCollaboration) {
    this.teamCollaboration = teamCollaboration;
  }

  public Boolean getWhiteLabel() {
    return whiteLabel;
  }

  public void setWhiteLabel(Boolean whiteLabel) {
    this.whiteLabel = whiteLabel;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public String getStripePriceIdMonthly() {
    return stripePriceIdMonthly;
  }

  public void setStripePriceIdMonthly(String stripePriceIdMonthly) {
    this.stripePriceIdMonthly = stripePriceIdMonthly;
  }

  public String getStripePriceIdYearly() {
    return stripePriceIdYearly;
  }

  public void setStripePriceIdYearly(String stripePriceIdYearly) {
    this.stripePriceIdYearly = stripePriceIdYearly;
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

  public List<UserSubscription> getUserSubscriptions() {
    return userSubscriptions;
  }

  public void setUserSubscriptions(List<UserSubscription> userSubscriptions) {
    this.userSubscriptions = userSubscriptions;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
