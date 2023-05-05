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
package org.candlepin.subscriptions.subscription;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.stream.Stream;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.files.ProductDenylist;
import org.candlepin.subscriptions.db.SubscriptionMeasurementRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Logic for pruning unlisted subscriptions (where the SKU is not in the denylist). */
@Slf4j
@Component
public class SubscriptionPruneController {
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionMeasurementRepository measurementRepository;
  private final OrgConfigRepository orgRepository;
  private final Timer pruneAllTimer;
  private final KafkaTemplate<String, PruneSubscriptionsTask>
      pruneSubscriptionsByOrgTaskKafkaTemplate;
  private final String pruneSubscriptionsTopic;
  private final ProductDenylist productDenylist;

  @Autowired
  public SubscriptionPruneController(
      SubscriptionRepository subscriptionRepository,
      SubscriptionMeasurementRepository measurementRepository,
      OrgConfigRepository orgRepository,
      MeterRegistry meterRegistry,
      KafkaTemplate<String, PruneSubscriptionsTask> pruneSubscriptionsByOrgTaskKafkaTemplate,
      ProductDenylist productDenylist,
      @Qualifier("pruneSubscriptionTasks") TaskQueueProperties pruneQueueProperties) {
    this.subscriptionRepository = subscriptionRepository;
    this.measurementRepository = measurementRepository;
    this.orgRepository = orgRepository;
    this.pruneAllTimer = meterRegistry.timer("swatch_subscription_prune_enqueue_all");
    this.productDenylist = productDenylist;
    this.pruneSubscriptionsTopic = pruneQueueProperties.getTopic();
    this.pruneSubscriptionsByOrgTaskKafkaTemplate = pruneSubscriptionsByOrgTaskKafkaTemplate;
  }

  public void pruneAllUnlistedSubscriptions() {
    Timer.Sample enqueueAllTime = Timer.start();
    orgRepository.findSyncEnabledOrgs().forEach(this::enqueueSubscriptionPrune);
    Duration enqueueAllDuration = Duration.ofNanos(enqueueAllTime.stop(pruneAllTimer));
    log.info(
        "Enqueued orgs to prune subscriptions in enqueueTimeMillis={}",
        enqueueAllDuration.toMillis());
  }

  @Transactional
  public void pruneUnlistedSubscriptions(String orgId) {
    Stream<Subscription> subscriptions = subscriptionRepository.findByOrgId(orgId);
    subscriptions.forEach(
        subscription -> {
          if (productDenylist.productIdMatches(subscription.getSku())) {
            log.info(
                "Removing subscriptionId={} for orgId={} w/ sku={}",
                subscription.getSubscriptionId(),
                orgId,
                subscription.getSku());
            subscriptionRepository.delete(subscription);
          }
        });
    Stream<SubscriptionMeasurement> measurementRecords =
        measurementRepository.findBySubscriptionOrgId(orgId);
    measurementRecords.forEach(
        measurement -> {
          var sku = measurement.getSubscription().getSku();
          if (productDenylist.productIdMatches(sku)) {
            log.info(
                "Removing measurement record for subscriptionId={} for orgId={} w/ sku={}",
                measurement.getSubscription().getSubscriptionId(),
                orgId,
                sku);
            measurementRepository.delete(measurement);
          }
        });
  }

  private void enqueueSubscriptionPrune(String orgId) {
    log.debug("Enqueuing subscription prune for orgId={}", orgId);
    pruneSubscriptionsByOrgTaskKafkaTemplate.send(
        pruneSubscriptionsTopic, PruneSubscriptionsTask.builder().orgId(orgId).build());
  }
}
