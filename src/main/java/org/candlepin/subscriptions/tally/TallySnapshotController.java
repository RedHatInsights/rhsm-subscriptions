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
package org.candlepin.subscriptions.tally;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import io.micrometer.core.annotation.Timed;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.TallyStateRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.TallyState;
import org.candlepin.subscriptions.db.model.TallyStateKey;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Provides the logic for updating Tally snapshots. */
@Component
public class TallySnapshotController {

  private static final Logger log = LoggerFactory.getLogger(TallySnapshotController.class);

  private final ApplicationProperties appProps;
  private final InventoryAccountUsageCollector usageCollector;
  private final EventController eventController;
  private final MetricUsageCollector metricUsageCollector;
  private final MaxSeenSnapshotStrategy maxSeenSnapshotStrategy;
  private final CombiningRollupSnapshotStrategy combiningRollupSnapshotStrategy;
  private final RetryTemplate retryTemplate;
  private final SnapshotSummaryProducer summaryProducer;
  private final TallyStateRepository tallyStateRepository;
  private final ApplicationClock clock;

  @Autowired
  public TallySnapshotController(
      ApplicationProperties appProps,
      InventoryAccountUsageCollector usageCollector,
      EventController eventController,
      MaxSeenSnapshotStrategy maxSeenSnapshotStrategy,
      @Qualifier("collectorRetryTemplate") RetryTemplate retryTemplate,
      MetricUsageCollector metricUsageCollector,
      CombiningRollupSnapshotStrategy combiningRollupSnapshotStrategy,
      SnapshotSummaryProducer summaryProducer,
      TallyStateRepository tallyStateRepository,
      ApplicationClock clock) {
    this.appProps = appProps;
    this.usageCollector = usageCollector;
    this.eventController = eventController;
    this.maxSeenSnapshotStrategy = maxSeenSnapshotStrategy;
    this.retryTemplate = retryTemplate;
    this.metricUsageCollector = metricUsageCollector;
    this.combiningRollupSnapshotStrategy = combiningRollupSnapshotStrategy;
    this.summaryProducer = summaryProducer;
    this.tallyStateRepository = tallyStateRepository;
    this.clock = clock;
  }

  @Timed("rhsm-subscriptions.snapshots.single")
  public void produceSnapshotsForOrg(String orgId) {
    if (Objects.isNull(orgId)) {
      throw new IllegalArgumentException("A non-null orgId is required for tally operations.");
    }

    log.info("Producing snapshots for Org ID {} ", orgId);

    AccountUsageCalculation accountCalc;
    try {
      accountCalc = performTally(orgId);

    } catch (Exception e) {
      log.error("Error collecting existing usage snapshots ", e);
      return;
    }

    maxSeenSnapshotStrategy.produceSnapshotsFromCalculations(accountCalc);
  }

  // Because we want to ensure that our DB operations have been completed before
  // any messages are sent, message sending must be done outside a DB transaction
  // boundary. We use Propagation.NEVER here so that if this method should ever be called
  // from within an existing DB transaction, an exception will be thrown.
  @Transactional(propagation = Propagation.NEVER)
  @Timed("rhsm-subscriptions.snapshots.single.hourly")
  public void produceHourlySnapshotsForOrg(String orgId) {
    if (Objects.isNull(orgId)) {
      throw new IllegalArgumentException("A non-null orgId is required for tally operations.");
    }

    log.info("Producing snapshots for Org ID {}.", orgId);
    // Because we would have already seen the events once by service type, the loop will result in a
    // retally if we fetch duplicate service types and loop through again, which is why we must use
    // Set in this situation rather than List. Set enables us to guarantee that each event is
    // fetched by service type just once.
    Set<String> serviceTypes = SubscriptionDefinition.getAllServiceTypes();
    for (String serviceType : serviceTypes) {
      log.info("Producing hourly snapshots for orgId {} for service type {} ", orgId, serviceType);

      try {
        TallyState currentState =
            tallyStateRepository
                .findById(new TallyStateKey(orgId, serviceType))
                .orElseGet(
                    () ->
                        tallyStateRepository.save(
                            new TallyState(orgId, serviceType, clock.startOfToday().minusDays(1))));

        AccountUsageCalculationCache calcCache = new AccountUsageCalculationCache();

        // Process events in batches until there are no more matches from the DB.
        boolean checkForEvents = true;
        while (checkForEvents) {
          List<Event> events =
              eventController.fetchEventsInBatch(
                  orgId,
                  serviceType,
                  currentState.getLatestEventRecordDate(),
                  appProps.getHourlyTallyEventBatchSize());

          if (events.isEmpty()) {
            checkForEvents = false;
            continue;
          }

          retryTemplate.execute(
              context -> {
                metricUsageCollector.updateHosts(orgId, serviceType, events);
                metricUsageCollector.calculateUsage(events, calcCache);
                currentState.setLatestEventRecordDate(
                    events.get(events.size() - 1).getRecordDate());
                return null;
              });
        }

        if (!calcCache.isEmpty()) {
          var applicableUsageCalculations =
              calcCache.getCalculations().entrySet().stream()
                  .filter(this::isCombiningRollupStrategySupported)
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

          Set<String> tags =
              SubscriptionDefinition.findByServiceType(serviceType).stream()
                  .map(SubscriptionDefinition::getVariants)
                  .flatMap(List::stream)
                  .map(Variant::getTag)
                  .collect(Collectors.toSet());

          Map<String, List<TallySnapshot>> totalSnapshots =
              combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
                  orgId,
                  calcCache.getCalculationRange(),
                  tags,
                  applicableUsageCalculations,
                  Granularity.HOURLY,
                  Double::sum);

          tallyStateRepository.update(currentState);
          summaryProducer.produceTallySummaryMessages(totalSnapshots);
        }

        log.info("Finished producing {} hourly snapshots for orgId {}", serviceType, orgId);
      } catch (Exception e) {
        log.error(
            "Could not collect {} metrics and/or produce snapshots for with orgId {}",
            serviceType,
            orgId,
            e);
      }
    }
  }

  private boolean isCombiningRollupStrategySupported(
      Map.Entry<OffsetDateTime, AccountUsageCalculation> usageCalculations) {

    var calculatedProducts = usageCalculations.getValue().getProducts();
    return calculatedProducts.stream()
        .map(SubscriptionDefinition::lookupSubscriptionByTag)
        .anyMatch(x -> x.map(SubscriptionDefinition::isPaygEligible).orElse(false));
  }

  private AccountUsageCalculation performTally(String orgId) {
    retryTemplate.execute(
        context -> {
          usageCollector.reconcileSystemDataWithHbi(
              orgId, SubscriptionDefinition.getAllNonPaygTags());
          return null;
        });
    return usageCollector.tally(orgId);
  }
}
