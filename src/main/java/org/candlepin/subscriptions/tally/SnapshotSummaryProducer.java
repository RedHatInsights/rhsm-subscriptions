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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/** Component that produces tally snapshot summary messages given a list of tally snapshots. */
@Service
public class SnapshotSummaryProducer {
  private static final Logger log = LoggerFactory.getLogger(SnapshotSummaryProducer.class);

  private final String tallySummaryTopic;
  private final KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate;
  private final RetryTemplate kafkaRetryTemplate;
  private final TallySummaryMapper summaryMapper;

  @Autowired
  protected SnapshotSummaryProducer(
      @Qualifier("tallySummaryKafkaTemplate")
          KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate,
      @Qualifier("tallySummaryKafkaRetryTemplate") RetryTemplate kafkaRetryTemplate,
      TallySummaryProperties props,
      TallySummaryMapper summaryMapper) {
    this.tallySummaryTopic = props.getTopic();
    this.kafkaRetryTemplate = kafkaRetryTemplate;
    this.tallySummaryKafkaTemplate = tallySummaryKafkaTemplate;
    this.summaryMapper = summaryMapper;
  }

  public void produceTallySummaryMessages(Map<String, List<TallySnapshot>> newAndUpdatedSnapshots) {
    AtomicInteger totalTallies = new AtomicInteger();
    newAndUpdatedSnapshots.forEach(
        (orgId, snapshots) ->
            /* Filter snapshots, as we only deal with hourly, non Any fields
            and measurement types other than Total
            when we transmit the tally summary message to the BillableUsage component. */
            snapshots.stream()
                .filter(this::filterByHourlyAndNotAnySnapshots)
                .map(
                    snapshot -> {
                      removeTotalMeasurements(snapshot);
                      return snapshot;
                    })
                .sorted(Comparator.comparing(TallySnapshot::getSnapshotDate))
                .map(snapshot -> summaryMapper.mapSnapshots(orgId, List.of(snapshot)))
                .forEach(
                    summary -> {
                      kafkaRetryTemplate.execute(
                          ctx -> tallySummaryKafkaTemplate.send(tallySummaryTopic, orgId, summary));
                      totalTallies.getAndIncrement();
                    }));

    log.info("Produced {} TallySummary messages", totalTallies);
  }

  private boolean filterByHourlyAndNotAnySnapshots(TallySnapshot snapshot) {
    return snapshot.getGranularity().equals(Granularity.HOURLY)
        && !snapshot.getServiceLevel().equals(ServiceLevel._ANY)
        && !snapshot.getUsage().equals(Usage._ANY)
        && !snapshot.getBillingProvider().equals(BillingProvider._ANY)
        && !snapshot.getBillingAccountId().equals(ResourceUtils.ANY)
        && hasMeasurements(snapshot);
  }

  private void removeTotalMeasurements(TallySnapshot snapshot) {
    if (Objects.nonNull(snapshot.getTallyMeasurements())) {
      snapshot
          .getTallyMeasurements()
          .entrySet()
          .removeIf(
              entry -> HardwareMeasurementType.TOTAL.equals(entry.getKey().getMeasurementType()));
    }
  }

  /**
   * Validates TallySnapshot measurements to make sure that it has all the information required by
   * the RH marketplace API. Any issues will be logged.
   *
   * @param snapshot the TallySnapshot to validate.
   * @return true if the TallySnapshot is valid, false otherwise.
   */
  private boolean hasMeasurements(TallySnapshot snapshot) {
    if (!Objects.nonNull(snapshot.getTallyMeasurements())
        || snapshot.getTallyMeasurements().isEmpty()) {
      log.warn(
          "Tally snapshot did not have measurements. "
              + "No usage will be sent to RH marketplace for this snapshot.\n{}",
          snapshot);
      return false;
    }

    return true;
  }
}
