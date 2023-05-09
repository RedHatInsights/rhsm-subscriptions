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
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.json.TallySummary;
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
            snapshots.stream()
                .map(
                    snapshot ->
                        summaryMapper.mapSnapshots(
                            snapshot.getAccountNumber(), orgId, List.of(snapshot)))
                .forEach(
                    summary -> {
                      if (validateTallySummary(summary)) {
                        kafkaRetryTemplate.execute(
                            ctx ->
                                tallySummaryKafkaTemplate.send(tallySummaryTopic, orgId, summary));
                        totalTallies.getAndIncrement();
                      }
                    }));

    log.info("Produced {} TallySummary messages", totalTallies);
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
