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

import com.redhat.swatch.configuration.registry.ProductId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/** Component that produces tally snapshot summary messages given a list of tally snapshots. */
@Slf4j
@Service
public class SnapshotSummaryProducer {

  private final String tallySummaryTopic;
  private final KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate;
  private final RetryTemplate kafkaRetryTemplate;
  private final TallySummaryMapper summaryMapper;

  public static Predicate<TallySnapshot> hourlySnapFilter =
      snapshot ->
          !ServiceLevel._ANY.equals(snapshot.getServiceLevel())
              && !Usage._ANY.equals(snapshot.getUsage())
              && !BillingProvider._ANY.equals(snapshot.getBillingProvider())
              && !ResourceUtils.ANY.equals(snapshot.getBillingAccountId())
              && hasMeasurements(snapshot);

  public static Predicate<TallySnapshot> nightlySnapFilter =
      snapshot ->
          !ServiceLevel._ANY.equals(snapshot.getServiceLevel())
              && !Usage._ANY.equals(snapshot.getUsage())
              && hasMeasurements(snapshot);

  public static Predicate<TallySnapshot> paygSnapFilter =
      snapshot ->
          Granularity.HOURLY.equals(snapshot.getGranularity())
                  && ProductId.fromString(snapshot.getProductId()).isPayg()
              || Granularity.DAILY.equals(snapshot.getGranularity())
                  && !ProductId.fromString(snapshot.getProductId()).isPayg();

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

  /**
   * Side effect: While producing tally summary messages for the given snapshots, TOTAL measurements
   * are removed from the original HOURLY snapshot objects that are passed in. The original DAILY
   * snapshot objects are intentionally left unmodified. This difference in behavior is due to the
   * usage of these same objects elsewhere in the process.
   */
  public void produceTallySummaryMessages(
      Map<String, List<TallySnapshot>> newAndUpdatedSnapshots,
      List<Granularity> granularities,
      Predicate<TallySnapshot> filter) {
    Map<Granularity, Map<String, List<TallySnapshot>>> groupedSnapshots =
        groupByGranularity(newAndUpdatedSnapshots, granularities);
    granularities.forEach(
        granularity -> {
          AtomicInteger totalTallies = new AtomicInteger();
          if (groupedSnapshots.get(granularity) != null) {
            groupedSnapshots
                .get(granularity)
                .forEach(
                    (orgId, snapshots) ->
                        /* Filter snapshots for specific granularity, non-Any fields
                        and measurement types other than Total
                        when we transmit the tally summary message to the BillableUsage component. */
                        snapshots.stream()
                            .filter(filter)
                            .filter(paygSnapFilter)
                            .map(
                                snapshot -> {
                                  removeTotalMeasurementsForHourly(snapshot);
                                  return removeTotalMeasurementsForDaily(snapshot);
                                })
                            .sorted(Comparator.comparing(TallySnapshot::getSnapshotDate))
                            .map(snapshot -> summaryMapper.mapSnapshots(orgId, List.of(snapshot)))
                            .forEach(
                                summary -> {
                                  kafkaRetryTemplate.execute(
                                      ctx ->
                                          tallySummaryKafkaTemplate.send(
                                              tallySummaryTopic, orgId, summary));
                                  totalTallies.getAndIncrement();
                                }));

            log.debug("Produced {} {} TallySummary messages", totalTallies, granularity);
          }
        });
  }

  public static TallySnapshot removeTotalMeasurementsForDaily(TallySnapshot snapshot) {
    if (Objects.nonNull(snapshot.getTallyMeasurements())
        && Granularity.DAILY.equals(snapshot.getGranularity())) {
      TallySnapshot snapshotCopy = deepCopy(snapshot);
      snapshotCopy.getTallyMeasurements().remove(HardwareMeasurementType.TOTAL);
      snapshotCopy
          .getTallyMeasurements()
          .entrySet()
          .removeIf(
              entry -> HardwareMeasurementType.TOTAL.equals(entry.getKey().getMeasurementType()));
      return snapshotCopy;
    }
    return snapshot;
  }

  public static void removeTotalMeasurementsForHourly(TallySnapshot snapshot) {
    if (Objects.nonNull(snapshot.getTallyMeasurements())
        && Granularity.HOURLY.equals(snapshot.getGranularity())) {
      snapshot
          .getTallyMeasurements()
          .entrySet()
          .removeIf(
              entry -> HardwareMeasurementType.TOTAL.equals(entry.getKey().getMeasurementType()));
    }
  }

  public static Map<Granularity, Map<String, List<TallySnapshot>>> groupByGranularity(
      Map<String, List<TallySnapshot>> snapshots, List<Granularity> granularities) {
    Map<Granularity, Map<String, List<TallySnapshot>>> result = new java.util.HashMap<>();
    snapshots.forEach(
        (orgId, snapshotList) ->
            snapshotList.stream()
                .filter(snapshot -> granularities.contains(snapshot.getGranularity()))
                .forEach(
                    snapshot -> {
                      Granularity granularity = snapshot.getGranularity();
                      result.putIfAbsent(granularity, new HashMap<>());
                      Map<String, List<TallySnapshot>> snapshotMap = result.get(granularity);
                      snapshotMap.putIfAbsent(orgId, new ArrayList<>());
                      snapshotMap.get(orgId).add(snapshot);
                    }));
    return result;
  }

  public static boolean filterByGranularityAndNotAnySnapshots(
      TallySnapshot snapshot, String granularity) {
    return snapshot.getGranularity() != null
        && snapshot.getGranularity().toString().equalsIgnoreCase(granularity)
        && !ServiceLevel._ANY.equals(snapshot.getServiceLevel())
        && !Usage._ANY.equals(snapshot.getUsage())
        && !BillingProvider._ANY.equals(snapshot.getBillingProvider())
        && !ResourceUtils.ANY.equals(snapshot.getBillingAccountId())
        && hasMeasurements(snapshot);
  }

  /**
   * Validates TallySnapshot measurements to make sure that it has all the information required by
   * the RH marketplace API. Any issues will be logged.
   *
   * @param snapshot the TallySnapshot to validate.
   * @return true if the TallySnapshot is valid, false otherwise.
   */
  public static boolean hasMeasurements(TallySnapshot snapshot) {
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

  /** Creates independent copy of tally snapshot */
  public static TallySnapshot deepCopy(TallySnapshot original) {
    if (original == null) {
      return null;
    }

    TallySnapshot copy = new TallySnapshot();
    copy.setOrgId(original.getOrgId());
    copy.setSnapshotDate(original.getSnapshotDate());
    copy.setProductId(original.getProductId());
    copy.setGranularity(original.getGranularity());
    copy.setServiceLevel(original.getServiceLevel());
    copy.setUsage(original.getUsage());
    copy.setBillingProvider(original.getBillingProvider());
    copy.setBillingAccountId(original.getBillingAccountId());

    // Deep copy the map to prevent side effects on the original collection
    if (original.getTallyMeasurements() != null) {
      copy.setTallyMeasurements(new HashMap<>(original.getTallyMeasurements()));
    }

    return copy;
  }
}
