package com.huskyapply.gateway.service;

import com.huskyapply.gateway.model.SubscriptionPlan;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.model.UserSubscription;
import com.huskyapply.gateway.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

  private final UserSubscriptionRepository userSubscriptionRepository;

  @Value("${stripe.secret.key:}")
  private String stripeSecretKey;

  @Value("${stripe.webhook.secret:}")
  private String stripeWebhookSecret;

  public PaymentService(UserSubscriptionRepository userSubscriptionRepository) {
    this.userSubscriptionRepository = userSubscriptionRepository;
  }

  /**
   * Create Stripe checkout session for subscription For now, this is a mock implementation. In
   * production, you would integrate with Stripe API.
   */
  public String createCheckoutSession(
      User user, SubscriptionPlan plan, String billingCycle, String successUrl, String cancelUrl) {
    logger.info("Creating checkout session for user {} and plan {}", user.getId(), plan.getName());

    // Mock implementation - in production, use Stripe API
    // Stripe.apiKey = stripeSecretKey;
    //
    // SessionCreateParams.Builder builder = SessionCreateParams.builder()
    //     .setSuccessUrl(successUrl)
    //     .setCancelUrl(cancelUrl)
    //     .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
    //     .setCustomerEmail(user.getEmail())
    //     .addLineItem(SessionCreateParams.LineItem.builder()
    //         .setPrice("MONTHLY".equals(billingCycle) ? plan.getStripePriceIdMonthly() :
    // plan.getStripePriceIdYearly())
    //         .setQuantity(1L)
    //         .build());
    //
    // Session session = Session.create(builder.build());
    // return session.getUrl();

    // Mock response for demonstration
    return "https://checkout.stripe.com/mock-session-" + java.util.UUID.randomUUID().toString();
  }

  /**
   * Handle successful payment from Stripe webhook Mock implementation - in production, this would
   * be called by Stripe webhooks
   */
  public UserSubscription handlePaymentSuccess(String stripeSessionId) {
    logger.info("Processing payment success for session: {}", stripeSessionId);

    // Mock implementation - create a dummy subscription
    // In production, you would:
    // 1. Verify webhook signature
    // 2. Extract subscription details from Stripe
    // 3. Update user subscription in database

    UserSubscription subscription = new UserSubscription();
    subscription.setStatus("ACTIVE");
    subscription.setStripeSubscriptionId("sub_" + java.util.UUID.randomUUID().toString());
    subscription.setCurrentPeriodStart(Instant.now());
    subscription.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));

    return userSubscriptionRepository.save(subscription);
  }

  /** Cancel Stripe subscription Mock implementation */
  public void cancelStripeSubscription(String stripeSubscriptionId) {
    logger.info("Cancelling Stripe subscription: {}", stripeSubscriptionId);

    // Mock implementation - in production:
    // Stripe.apiKey = stripeSecretKey;
    // Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
    // subscription.cancel();
  }

  /** Reactivate Stripe subscription Mock implementation */
  public void reactivateStripeSubscription(String stripeSubscriptionId) {
    logger.info("Reactivating Stripe subscription: {}", stripeSubscriptionId);

    // Mock implementation - in production:
    // Stripe.apiKey = stripeSecretKey;
    // Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
    // SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
    //     .setCancelAtPeriodEnd(false)
    //     .build();
    // subscription.update(params);
  }

  /** Process Stripe webhook events Mock implementation */
  public void handleWebhookEvent(String payload, String signatureHeader) {
    logger.info("Processing Stripe webhook event");

    // Mock implementation - in production:
    // Event event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
    //
    // switch (event.getType()) {
    //     case "checkout.session.completed":
    //         handleCheckoutCompleted(event);
    //         break;
    //     case "customer.subscription.updated":
    //         handleSubscriptionUpdated(event);
    //         break;
    //     case "customer.subscription.deleted":
    //         handleSubscriptionDeleted(event);
    //         break;
    //     case "invoice.payment_failed":
    //         handlePaymentFailed(event);
    //         break;
    //     default:
    //         logger.warn("Unhandled Stripe event type: {}", event.getType());
    // }
  }

  /** Get customer portal URL for subscription management Mock implementation */
  public String createCustomerPortalSession(User user, String returnUrl) {
    logger.info("Creating customer portal session for user: {}", user.getId());

    // Mock implementation - in production:
    // Stripe.apiKey = stripeSecretKey;
    //
    // Optional<UserSubscription> subscription =
    // userSubscriptionRepository.findActiveSubscriptionByUser(user);
    // if (subscription.isEmpty()) {
    //     throw new IllegalStateException("No active subscription found");
    // }
    //
    // BillingPortalSessionCreateParams params = BillingPortalSessionCreateParams.builder()
    //     .setCustomer(subscription.get().getStripeCustomerId())
    //     .setReturnUrl(returnUrl)
    //     .build();
    //
    // BillingPortalSession portalSession = BillingPortalSession.create(params);
    // return portalSession.getUrl();

    return "https://billing.stripe.com/mock-portal-" + java.util.UUID.randomUUID().toString();
  }
}
