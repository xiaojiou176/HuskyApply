package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.SubscriptionPlanResponse;
import com.huskyapply.gateway.dto.UserSubscriptionResponse;
import com.huskyapply.gateway.exception.ResourceNotFoundException;
import com.huskyapply.gateway.exception.SubscriptionLimitExceededException;
import com.huskyapply.gateway.model.SubscriptionPlan;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.model.UserSubscription;
import com.huskyapply.gateway.repository.SubscriptionPlanRepository;
import com.huskyapply.gateway.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionService {

  private final SubscriptionPlanRepository subscriptionPlanRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final PaymentService paymentService;

  public SubscriptionService(
      SubscriptionPlanRepository subscriptionPlanRepository,
      UserSubscriptionRepository userSubscriptionRepository,
      PaymentService paymentService) {
    this.subscriptionPlanRepository = subscriptionPlanRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.paymentService = paymentService;
  }

  /** Get all active subscription plans */
  @Transactional(readOnly = true)
  public List<SubscriptionPlanResponse> getAvailablePlans() {
    List<SubscriptionPlan> plans =
        subscriptionPlanRepository.findByIsActiveTrueOrderByPriceMonthlyAsc();
    return plans.stream().map(this::toSubscriptionPlanResponse).collect(Collectors.toList());
  }

  /** Get user's current active subscription */
  @Transactional(readOnly = true)
  public Optional<UserSubscriptionResponse> getUserActiveSubscription(User user) {
    Optional<UserSubscription> subscription =
        userSubscriptionRepository.findActiveSubscriptionByUser(user);
    return subscription.map(this::toUserSubscriptionResponse);
  }

  /** Get user's subscription history */
  @Transactional(readOnly = true)
  public List<UserSubscriptionResponse> getUserSubscriptionHistory(User user) {
    List<UserSubscription> subscriptions =
        userSubscriptionRepository.findByUserOrderByCreatedAtDesc(user);
    return subscriptions.stream()
        .map(this::toUserSubscriptionResponse)
        .collect(Collectors.toList());
  }

  /** Check if user can create a job (within limits) */
  @Transactional(readOnly = true)
  public void validateJobCreationLimits(User user) {
    Optional<UserSubscription> subscription =
        userSubscriptionRepository.findActiveSubscriptionByUser(user);

    if (subscription.isEmpty()) {
      throw new SubscriptionLimitExceededException("No active subscription found");
    }

    UserSubscription userSub = subscription.get();
    if (!userSub.hasJobsRemaining()) {
      throw new SubscriptionLimitExceededException(
          String.format(
              "Job limit reached. You have used %d/%d jobs this period. Upgrade your plan for more jobs.",
              userSub.getJobsUsedThisPeriod(), userSub.getSubscriptionPlan().getJobsPerMonth()));
    }
  }

  /** Check if user can create a template (within limits) */
  @Transactional(readOnly = true)
  public void validateTemplateCreationLimits(User user, int currentTemplateCount) {
    Optional<UserSubscription> subscription =
        userSubscriptionRepository.findActiveSubscriptionByUser(user);

    if (subscription.isEmpty()) {
      throw new SubscriptionLimitExceededException("No active subscription found");
    }

    UserSubscription userSub = subscription.get();
    Integer templateLimit = userSub.getSubscriptionPlan().getTemplatesLimit();

    if (templateLimit != null && currentTemplateCount >= templateLimit) {
      throw new SubscriptionLimitExceededException(
          String.format(
              "Template limit reached. You have %d/%d templates. Upgrade your plan for more templates.",
              currentTemplateCount, templateLimit));
    }
  }

  /** Check if user can create a batch job (within limits) */
  @Transactional(readOnly = true)
  public void validateBatchJobCreationLimits(User user, int currentBatchJobCount) {
    Optional<UserSubscription> subscription =
        userSubscriptionRepository.findActiveSubscriptionByUser(user);

    if (subscription.isEmpty()) {
      throw new SubscriptionLimitExceededException("No active subscription found");
    }

    UserSubscription userSub = subscription.get();
    Integer batchJobLimit = userSub.getSubscriptionPlan().getBatchJobsLimit();

    if (batchJobLimit != null && currentBatchJobCount >= batchJobLimit) {
      throw new SubscriptionLimitExceededException(
          String.format(
              "Batch job limit reached. You have %d/%d batch jobs. Upgrade your plan for more batch jobs.",
              currentBatchJobCount, batchJobLimit));
    }
  }

  /** Check if user has access to specific AI model */
  @Transactional(readOnly = true)
  public boolean hasModelAccess(User user, String modelProvider, String modelName) {
    Optional<UserSubscription> subscription =
        userSubscriptionRepository.findActiveSubscriptionByUser(user);

    if (subscription.isEmpty()) {
      return false;
    }

    UserSubscription userSub = subscription.get();
    String allowedModels = userSub.getSubscriptionPlan().getAiModelsAccess();

    // If no restrictions, allow all models
    if (allowedModels == null || allowedModels.isEmpty()) {
      return true;
    }

    // Parse JSON array and check if model is allowed
    String modelToCheck = modelName != null ? modelName : getDefaultModelForProvider(modelProvider);
    return allowedModels.contains(modelToCheck);
  }

  /** Create checkout session for subscription upgrade */
  public String createCheckoutSession(
      User user, UUID planId, String billingCycle, String successUrl, String cancelUrl) {
    SubscriptionPlan plan =
        subscriptionPlanRepository
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

    return paymentService.createCheckoutSession(user, plan, billingCycle, successUrl, cancelUrl);
  }

  /** Handle successful payment and activate subscription */
  public UserSubscriptionResponse handlePaymentSuccess(String stripeSessionId) {
    // This will be called by webhook from Stripe
    UserSubscription subscription = paymentService.handlePaymentSuccess(stripeSessionId);
    return toUserSubscriptionResponse(subscription);
  }

  /** Cancel subscription at period end */
  public UserSubscriptionResponse cancelSubscription(User user) {
    Optional<UserSubscription> subscription =
        userSubscriptionRepository.findActiveSubscriptionByUser(user);

    if (subscription.isEmpty()) {
      throw new ResourceNotFoundException("No active subscription found");
    }

    UserSubscription userSub = subscription.get();
    userSub.setCancelAtPeriodEnd(true);
    userSub.setUpdatedAt(Instant.now());

    // Also cancel in Stripe
    if (userSub.getStripeSubscriptionId() != null) {
      paymentService.cancelStripeSubscription(userSub.getStripeSubscriptionId());
    }

    userSub = userSubscriptionRepository.save(userSub);
    return toUserSubscriptionResponse(userSub);
  }

  /** Reactivate cancelled subscription */
  public UserSubscriptionResponse reactivateSubscription(User user) {
    Optional<UserSubscription> subscription =
        userSubscriptionRepository.findActiveSubscriptionByUser(user);

    if (subscription.isEmpty()) {
      throw new ResourceNotFoundException("No active subscription found");
    }

    UserSubscription userSub = subscription.get();
    userSub.setCancelAtPeriodEnd(false);
    userSub.setUpdatedAt(Instant.now());

    // Also reactivate in Stripe
    if (userSub.getStripeSubscriptionId() != null) {
      paymentService.reactivateStripeSubscription(userSub.getStripeSubscriptionId());
    }

    userSub = userSubscriptionRepository.save(userSub);
    return toUserSubscriptionResponse(userSub);
  }

  private String getDefaultModelForProvider(String provider) {
    switch (provider.toLowerCase()) {
      case "openai":
        return "gpt-4o";
      case "anthropic":
        return "claude-3-5-sonnet-20241022";
      default:
        return "gpt-3.5-turbo";
    }
  }

  private SubscriptionPlanResponse toSubscriptionPlanResponse(SubscriptionPlan plan) {
    return new SubscriptionPlanResponse(
        plan.getId().toString(),
        plan.getName(),
        plan.getDescription(),
        plan.getPriceMonthly(),
        plan.getPriceYearly(),
        plan.getJobsPerMonth(),
        plan.getTemplatesLimit(),
        plan.getBatchJobsLimit(),
        plan.getAiModelsAccess(),
        plan.getPriorityProcessing(),
        plan.getApiAccess(),
        plan.getTeamCollaboration(),
        plan.getWhiteLabel());
  }

  private UserSubscriptionResponse toUserSubscriptionResponse(UserSubscription subscription) {
    SubscriptionPlanResponse plan = toSubscriptionPlanResponse(subscription.getSubscriptionPlan());

    return new UserSubscriptionResponse(
        subscription.getId().toString(),
        plan,
        subscription.getStatus(),
        subscription.getBillingCycle(),
        subscription.getCurrentPeriodStart(),
        subscription.getCurrentPeriodEnd(),
        subscription.getJobsUsedThisPeriod(),
        subscription.getJobsRemaining(),
        subscription.getCancelAtPeriodEnd(),
        subscription.getTrialEnd(),
        subscription.getCreatedAt());
  }
}
