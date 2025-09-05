package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.model.UserSubscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

  Optional<UserSubscription> findByUserAndStatus(User user, String status);

  @Query(
      "SELECT us FROM UserSubscription us WHERE us.user = :user AND us.status = 'ACTIVE' ORDER BY us.createdAt DESC")
  Optional<UserSubscription> findActiveSubscriptionByUser(@Param("user") User user);

  Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

  List<UserSubscription> findByUserOrderByCreatedAtDesc(User user);

  @Query(
      "SELECT us FROM UserSubscription us WHERE us.currentPeriodEnd < :now AND us.status = 'ACTIVE'")
  List<UserSubscription> findExpiredActiveSubscriptions(@Param("now") Instant now);

  @Query(
      "SELECT us FROM UserSubscription us WHERE us.currentPeriodEnd BETWEEN :start AND :end AND us.status = 'ACTIVE'")
  List<UserSubscription> findSubscriptionsExpiringBetween(
      @Param("start") Instant start, @Param("end") Instant end);

  @Query(
      "SELECT us FROM UserSubscription us WHERE us.trialEnd < :now AND us.trialEnd IS NOT NULL AND us.status = 'ACTIVE'")
  List<UserSubscription> findExpiredTrials(@Param("now") Instant now);

  @Query(
      "SELECT COUNT(us) FROM UserSubscription us WHERE us.subscriptionPlan.id = :planId AND us.status = 'ACTIVE'")
  long countActiveSubscriptionsByPlan(@Param("planId") UUID planId);

  @Query(
      "SELECT us FROM UserSubscription us WHERE us.status = 'ACTIVE' AND us.jobsUsedThisPeriod >= us.subscriptionPlan.jobsPerMonth AND us.subscriptionPlan.jobsPerMonth IS NOT NULL")
  List<UserSubscription> findSubscriptionsAtJobLimit();

  @Query(
      "SELECT COALESCE(SUM(us.jobsUsedThisPeriod), 0) FROM UserSubscription us WHERE us.user = :user AND us.currentPeriodStart <= :now AND us.currentPeriodEnd > :now")
  long getTotalJobsUsedThisPeriod(@Param("user") User user, @Param("now") Instant now);
}
