package com.huskyapply.gateway.dto;

import java.math.BigDecimal;

public class SubscriptionPlanResponse {

  private String id;
  private String name;
  private String description;
  private BigDecimal priceMonthly;
  private BigDecimal priceYearly;
  private Integer jobsPerMonth;
  private Integer templatesLimit;
  private Integer batchJobsLimit;
  private String aiModelsAccess;
  private Boolean priorityProcessing;
  private Boolean apiAccess;
  private Boolean teamCollaboration;
  private Boolean whiteLabel;

  public SubscriptionPlanResponse() {}

  public SubscriptionPlanResponse(
      String id,
      String name,
      String description,
      BigDecimal priceMonthly,
      BigDecimal priceYearly,
      Integer jobsPerMonth,
      Integer templatesLimit,
      Integer batchJobsLimit,
      String aiModelsAccess,
      Boolean priorityProcessing,
      Boolean apiAccess,
      Boolean teamCollaboration,
      Boolean whiteLabel) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.priceMonthly = priceMonthly;
    this.priceYearly = priceYearly;
    this.jobsPerMonth = jobsPerMonth;
    this.templatesLimit = templatesLimit;
    this.batchJobsLimit = batchJobsLimit;
    this.aiModelsAccess = aiModelsAccess;
    this.priorityProcessing = priorityProcessing;
    this.apiAccess = apiAccess;
    this.teamCollaboration = teamCollaboration;
    this.whiteLabel = whiteLabel;
  }

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
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

  // Helper methods
  public boolean isUnlimitedJobs() {
    return jobsPerMonth == null;
  }

  public boolean isUnlimitedTemplates() {
    return templatesLimit == null;
  }

  public boolean isUnlimitedBatchJobs() {
    return batchJobsLimit == null;
  }

  public BigDecimal getYearlySavings() {
    if (priceMonthly != null && priceYearly != null) {
      BigDecimal monthlyAnnual = priceMonthly.multiply(BigDecimal.valueOf(12));
      return monthlyAnnual.subtract(priceYearly);
    }
    return BigDecimal.ZERO;
  }
}
