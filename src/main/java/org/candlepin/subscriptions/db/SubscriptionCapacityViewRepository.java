package org.candlepin.subscriptions.db;

import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionCapacityViewRepository
        extends JpaRepository<SubscriptionCapacityView, SubscriptionCapacityKey> {

  /*  @Query(
            "SELECT s FROM SubscriptionCapacityView s where "
                    + "s.ownerId = :ownerId AND s.productId = :productId AND s.sla = :sla AND s.usage = :usage")
    List<SubscriptionCapacityView> findByKeyOwnerIdAndKeyProductId(
            @Param("ownerId") String ownerId,
            @Param("productId") String productId,
            @Param("sla") ServiceLevel sla,
            @Param("usage") Usage usage);*/

    List<SubscriptionCapacityView> findByKeyOwnerIdAndKeyProductIdAndServiceLevelAndUsage(String ownerId, String productId, ServiceLevel serviceLevel, Usage usage);
}
