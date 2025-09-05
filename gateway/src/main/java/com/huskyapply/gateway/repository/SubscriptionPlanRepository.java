package com.huskyapply.gateway.repository;

import com.huskyapply.gateway.model.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

  List<SubscriptionPlan> findByIsActiveTrueOrderByPriceMonthlyAsc();

  Optional<SubscriptionPlan> findByNameAndIsActiveTrue(String name);

  @Query(
      "SELECT sp FROM SubscriptionPlan sp WHERE sp.stripePriceIdMonthly = ?1 OR sp.stripePriceIdYearly = ?1")
  Optional<SubscriptionPlan> findByStripePriceId(String stripePriceId);

  List<SubscriptionPlan> findByWhiteLabelTrue();

  List<SubscriptionPlan> findByApiAccessTrue();

  List<SubscriptionPlan> findByTeamCollaborationTrue();

  @Query(
      "SELECT COUNT(us) FROM UserSubscription us WHERE us.subscriptionPlan = ?1 AND us.status = 'ACTIVE'")
  long countActiveSubscriptions(SubscriptionPlan plan);
}
