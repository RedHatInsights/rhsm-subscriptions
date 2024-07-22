/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.contract.model.ReconcileCapacityByOfferingTask;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.service.CapacityReconciliationService;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Running kafka w/ kubedock broken, see https://github.com/joyrex2001/kubedock/issues/53")
@QuarkusTest
@QuarkusTestResource(value = KafkaCompanionResource.class, restrictToAnnotatedClass = true)
class CapacityReconciliationIntegrationTest {
  @InjectKafkaCompanion KafkaCompanion companion;

  @Inject CapacityReconciliationService reconciliationService;

  @Inject SubscriptionRepository subscriptionRepository;

  @Inject OfferingRepository offeringRepository;

  @Transactional
  void createTestData() {
    var offering = new OfferingEntity();
    offering.setSku("BASILISK");
    offeringRepository.persist(offering);
    SubscriptionEntity subscription = new SubscriptionEntity();
    subscription.setSubscriptionId("subId");
    subscription.setStartDate(OffsetDateTime.now());
    var measurement = new SubscriptionMeasurementEntity();
    measurement.setSubscription(subscription);
    measurement.setMeasurementType("PHYSICAL");
    measurement.setMetricId("Cores");
    measurement.setValue(42.0);
    subscription.getSubscriptionMeasurements().add(measurement);
    subscription.setOffering(offering);
    subscriptionRepository.persist(subscription);
  }

  @Test
  void testReconciliationCreatesAnotherMessage() {
    createTestData();

    companion.registerSerde(
        ReconcileCapacityByOfferingTask.class,
        new ObjectMapperSerde<>(ReconcileCapacityByOfferingTask.class));
    reconciliationService.reconcileCapacityForOffering("BASILISK", 0, 1);
    var task =
        companion
            .consume(ReconcileCapacityByOfferingTask.class)
            .fromTopics("platform.rhsm-subscriptions.capacity-reconcile", 1)
            .awaitNextRecords(1, Duration.ofSeconds(5));
    assertEquals(1, task.getFirstRecord().value().getOffset());
  }
}
