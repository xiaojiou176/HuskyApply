package com.huskyapply.gateway.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_plan_id", nullable = false)
  private SubscriptionPlan subscriptionPlan;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE"; // ACTIVE, CANCELLED, EXPIRED, PAST_DUE

  @Column(name = "billing_cycle", nullable = false, length = 10)
  private String billingCycle = "MONTHLY"; // MONTHLY, YEARLY

  @Column(name = "stripe_subscription_id", length = 100)
  private String stripeSubscriptionId;

  @Column(name = "stripe_customer_id", length = 100)
  private String stripeCustomerId;

  @Column(name = "current_period_start", nullable = false)
  private Instant currentPeriodStart;

  @Column(name = "current_period_end", nullable = false)
  private Instant currentPeriodEnd;

  @Column(name = "jobs_used_this_period")
  private Integer jobsUsedThisPeriod = 0;

  @Column(name = "cancel_at_period_end")
  private Boolean cancelAtPeriodEnd = false;

  @Column(name = "trial_end")
  private Instant trialEnd;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  public UserSubscription() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    this.currentPeriodStart = now;
    this.currentPeriodEnd = now.plus(30, java.time.temporal.ChronoUnit.DAYS); // Default 30 days
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public SubscriptionPlan getSubscriptionPlan() {
    return subscriptionPlan;
  }

  public void setSubscriptionPlan(SubscriptionPlan subscriptionPlan) {
    this.subscriptionPlan = subscriptionPlan;
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

  public String getStripeSubscriptionId() {
    return stripeSubscriptionId;
  }

  public void setStripeSubscriptionId(String stripeSubscriptionId) {
    this.stripeSubscriptionId = stripeSubscriptionId;
  }

  public String getStripeCustomerId() {
    return stripeCustomerId;
  }

  public void setStripeCustomerId(String stripeCustomerId) {
    this.stripeCustomerId = stripeCustomerId;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public void setCancelledAt(Instant cancelledAt) {
    this.cancelledAt = cancelledAt;
  }

  // Utility methods
  public boolean isActive() {
    return "ACTIVE".equals(status) && Instant.now().isBefore(currentPeriodEnd);
  }

  public boolean hasJobsRemaining() {
    if (subscriptionPlan == null || subscriptionPlan.getJobsPerMonth() == null) {
      return true; // Unlimited plan
    }
    return jobsUsedThisPeriod < subscriptionPlan.getJobsPerMonth();
  }

  public int getJobsRemaining() {
    if (subscriptionPlan == null || subscriptionPlan.getJobsPerMonth() == null) {
      return Integer.MAX_VALUE; // Unlimited
    }
    return Math.max(0, subscriptionPlan.getJobsPerMonth() - jobsUsedThisPeriod);
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
