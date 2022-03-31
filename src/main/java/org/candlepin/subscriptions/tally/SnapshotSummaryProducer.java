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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Component that produces tally snapshot summary messages given a list of tally snapshots. */
@Service
public class SnapshotSummaryProducer {
  private static final Logger log = LoggerFactory.getLogger(SnapshotSummaryProducer.class);

  private final String tallySummaryTopic;
  private final KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate;

  @Autowired
  protected SnapshotSummaryProducer(
      KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate,
      @Qualifier("rhMarketplaceTasks") TaskQueueProperties props) {
    this.tallySummaryTopic = props.getTopic();
    this.tallySummaryKafkaTemplate = tallySummaryKafkaTemplate;
  }

  public void produceTallySummaryMessages(Map<String, List<TallySnapshot>> newAndUpdatedSnapshots) {
    AtomicInteger totalTallies = new AtomicInteger();
    newAndUpdatedSnapshots.forEach(
        (account, snapshots) ->
            snapshots.stream()
                .map(snapshot -> createTallySummary(account, List.of(snapshot)))
                .forEach(
                    summary -> {
                      if (validateTallySummary(summary)) {
                        tallySummaryKafkaTemplate.send(tallySummaryTopic, summary);
                        totalTallies.getAndIncrement();
                      }
                    }));

    log.info("Produced {} TallySummary messages", totalTallies);
  }

  private TallySummary createTallySummary(
      String accountNumber, List<TallySnapshot> tallySnapshots) {
    var mappedSnapshots =
        tallySnapshots.stream().map(this::mapTallySnapshot).collect(Collectors.toList());
    return new TallySummary().withAccountNumber(accountNumber).withTallySnapshots(mappedSnapshots);
  }

  private org.candlepin.subscriptions.json.TallySnapshot mapTallySnapshot(
      TallySnapshot tallySnapshot) {

    var granularity =
        org.candlepin.subscriptions.json.TallySnapshot.Granularity.fromValue(
            tallySnapshot.getGranularity().getValue());

    var sla =
        org.candlepin.subscriptions.json.TallySnapshot.Sla.fromValue(
            tallySnapshot.getServiceLevel().getValue());

    var usage =
        org.candlepin.subscriptions.json.TallySnapshot.Usage.fromValue(
            tallySnapshot.getUsage().getValue());

    var billingProvider =
        org.candlepin.subscriptions.json.TallySnapshot.BillingProvider.fromValue(
            tallySnapshot.getBillingProvider().getValue());

    return new org.candlepin.subscriptions.json.TallySnapshot()
        .withGranularity(granularity)
        .withId(tallySnapshot.getId())
        .withProductId(tallySnapshot.getProductId())
        .withSnapshotDate(tallySnapshot.getSnapshotDate())
        .withSla(sla)
        .withUsage(usage)
        .withBillingProvider(billingProvider)
        .withTallyMeasurements(mapMeasurements(tallySnapshot.getTallyMeasurements()));
  }

  private List<TallyMeasurement> mapMeasurements(
      Map<TallyMeasurementKey, Double> tallyMeasurements) {
    return tallyMeasurements.entrySet().stream()
        .map(
            entry ->
                new TallyMeasurement()
                    .withHardwareMeasurementType(entry.getKey().getMeasurementType().toString())
                    .withUom(TallyMeasurement.Uom.fromValue(entry.getKey().getUom().value()))
                    .withValue(entry.getValue()))
        .collect(Collectors.toList());
  }

  /**
   * Validates a TallySummary to make sure that it has all the information required by the RH
   * marketplace API. Any issues will be logged.
   *
   * @param summary the summary to validate.
   * @return true if the TallySummary is valid, false otherwise.
   */
  private boolean validateTallySummary(TallySummary summary) {
    // RH Marketplace requires at least one measurement be included in the Event
    Optional<org.candlepin.subscriptions.json.TallySnapshot> invalidDueToMeasurements =
        summary.getTallySnapshots().stream()
            .filter(snap -> snap.getTallyMeasurements().isEmpty())
            .findFirst();
    if (invalidDueToMeasurements.isPresent()) {
      log.warn(
          "One or more tally summary snapshots did not have measurements. "
              + "No usage will be sent to RH marketplace for this summary.\n{}",
          summary);
      return false;
    }

    return true;
  }
}
