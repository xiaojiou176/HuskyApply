package com.huskyapply.gateway.controller;

import com.huskyapply.gateway.dto.SubscriptionPlanResponse;
import com.huskyapply.gateway.dto.UserSubscriptionResponse;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.service.PaymentService;
import com.huskyapply.gateway.service.SubscriptionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

  private final SubscriptionService subscriptionService;
  private final PaymentService paymentService;

  public SubscriptionController(
      SubscriptionService subscriptionService, PaymentService paymentService) {
    this.subscriptionService = subscriptionService;
    this.paymentService = paymentService;
  }

  @GetMapping("/plans")
  public ResponseEntity<List<SubscriptionPlanResponse>> getAvailablePlans() {
    List<SubscriptionPlanResponse> plans = subscriptionService.getAvailablePlans();
    return ResponseEntity.ok(plans);
  }

  @GetMapping("/current")
  public ResponseEntity<UserSubscriptionResponse> getCurrentSubscription(
      @AuthenticationPrincipal User user) {
    Optional<UserSubscriptionResponse> subscription =
        subscriptionService.getUserActiveSubscription(user);
    return subscription.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/history")
  public ResponseEntity<List<UserSubscriptionResponse>> getSubscriptionHistory(
      @AuthenticationPrincipal User user) {
    List<UserSubscriptionResponse> history = subscriptionService.getUserSubscriptionHistory(user);
    return ResponseEntity.ok(history);
  }

  @PostMapping("/checkout")
  public ResponseEntity<Map<String, String>> createCheckoutSession(
      @AuthenticationPrincipal User user,
      @RequestParam UUID planId,
      @RequestParam(defaultValue = "MONTHLY") String billingCycle,
      @RequestParam String successUrl,
      @RequestParam String cancelUrl) {

    String checkoutUrl =
        subscriptionService.createCheckoutSession(
            user, planId, billingCycle, successUrl, cancelUrl);
    return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
  }

  @PostMapping("/cancel")
  public ResponseEntity<UserSubscriptionResponse> cancelSubscription(
      @AuthenticationPrincipal User user) {
    UserSubscriptionResponse subscription = subscriptionService.cancelSubscription(user);
    return ResponseEntity.ok(subscription);
  }

  @PostMapping("/reactivate")
  public ResponseEntity<UserSubscriptionResponse> reactivateSubscription(
      @AuthenticationPrincipal User user) {
    UserSubscriptionResponse subscription = subscriptionService.reactivateSubscription(user);
    return ResponseEntity.ok(subscription);
  }

  @GetMapping("/portal")
  public ResponseEntity<Map<String, String>> getCustomerPortal(
      @AuthenticationPrincipal User user, @RequestParam String returnUrl) {

    String portalUrl = paymentService.createCustomerPortalSession(user, returnUrl);
    return ResponseEntity.ok(Map.of("portalUrl", portalUrl));
  }

  @PostMapping("/webhook")
  public ResponseEntity<String> handleStripeWebhook(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String signatureHeader) {

    try {
      paymentService.handleWebhookEvent(payload, signatureHeader);
      return ResponseEntity.ok("Webhook processed successfully");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Webhook processing failed: " + e.getMessage());
    }
  }

  @GetMapping("/usage")
  public ResponseEntity<Map<String, Object>> getUsageStats(@AuthenticationPrincipal User user) {
    Optional<UserSubscriptionResponse> subscription =
        subscriptionService.getUserActiveSubscription(user);

    if (subscription.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    UserSubscriptionResponse sub = subscription.get();
    Map<String, Object> usage =
        Map.of(
            "jobsUsed", sub.getJobsUsedThisPeriod(),
            "jobsRemaining", sub.getJobsRemaining(),
            "jobsLimit",
                sub.getPlan().getJobsPerMonth() != null ? sub.getPlan().getJobsPerMonth() : -1,
            "usagePercentage", sub.getJobUsagePercentage(),
            "periodStart", sub.getCurrentPeriodStart(),
            "periodEnd", sub.getCurrentPeriodEnd(),
            "daysUntilRenewal", sub.getDaysUntilRenewal());

    return ResponseEntity.ok(usage);
  }
}
