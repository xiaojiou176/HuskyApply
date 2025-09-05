package com.huskyapply.gateway.dto;

import java.time.Instant;

public class UserSubscriptionResponse {

  private String id;
  private SubscriptionPlanResponse plan;
  private String status;
  private String billingCycle;
  private Instant currentPeriodStart;
  private Instant currentPeriodEnd;
  private Integer jobsUsedThisPeriod;
  private Integer jobsRemaining;
  private Boolean cancelAtPeriodEnd;
  private Instant trialEnd;
  private Instant createdAt;

  public UserSubscriptionResponse() {}

  public UserSubscriptionResponse(
      String id,
      SubscriptionPlanResponse plan,
      String status,
      String billingCycle,
      Instant currentPeriodStart,
      Instant currentPeriodEnd,
      Integer jobsUsedThisPeriod,
      Integer jobsRemaining,
      Boolean cancelAtPeriodEnd,
      Instant trialEnd,
      Instant createdAt) {
    this.id = id;
    this.plan = plan;
    this.status = status;
    this.billingCycle = billingCycle;
    this.currentPeriodStart = currentPeriodStart;
    this.currentPeriodEnd = currentPeriodEnd;
    this.jobsUsedThisPeriod = jobsUsedThisPeriod;
    this.jobsRemaining = jobsRemaining;
    this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    this.trialEnd = trialEnd;
    this.createdAt = createdAt;
  }

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public SubscriptionPlanResponse getPlan() {
    return plan;
  }

  public void setPlan(SubscriptionPlanResponse plan) {
    this.plan = plan;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getBillingCycle() {
    return billingCycle;
  }

  public void setBillingCycle(String billingCycle) {
    this.billingCycle = billingCycle;
  }

  public Instant getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public void setCurrentPeriodStart(Instant currentPeriodStart) {
    this.currentPeriodStart = currentPeriodStart;
  }

  public Instant getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
  }

  public Integer getJobsUsedThisPeriod() {
    return jobsUsedThisPeriod;
  }

  public void setJobsUsedThisPeriod(Integer jobsUsedThisPeriod) {
    this.jobsUsedThisPeriod = jobsUsedThisPeriod;
  }

  public Integer getJobsRemaining() {
    return jobsRemaining;
  }

  public void setJobsRemaining(Integer jobsRemaining) {
    this.jobsRemaining = jobsRemaining;
  }

  public Boolean getCancelAtPeriodEnd() {
    return cancelAtPeriodEnd;
  }

  public void setCancelAtPeriodEnd(Boolean cancelAtPeriodEnd) {
    this.cancelAtPeriodEnd = cancelAtPeriodEnd;
  }

  public Instant getTrialEnd() {
    return trialEnd;
  }

  public void setTrialEnd(Instant trialEnd) {
    this.trialEnd = trialEnd;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  // Helper methods
  public boolean isActive() {
    return "ACTIVE".equals(status) && Instant.now().isBefore(currentPeriodEnd);
  }

  public boolean isOnTrial() {
    return trialEnd != null && Instant.now().isBefore(trialEnd);
  }

  public long getDaysUntilRenewal() {
    if (currentPeriodEnd == null) return 0;
    return java.time.Duration.between(Instant.now(), currentPeriodEnd).toDays();
  }

  public double getJobUsagePercentage() {
    if (plan == null || plan.getJobsPerMonth() == null) return 0.0; // Unlimited plan
    if (plan.getJobsPerMonth() == 0) return 100.0;
    return (jobsUsedThisPeriod.doubleValue() / plan.getJobsPerMonth().doubleValue()) * 100.0;
  }
}
