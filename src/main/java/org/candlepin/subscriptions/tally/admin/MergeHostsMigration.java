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
package org.candlepin.subscriptions.tally.admin;

import com.redhat.swatch.configuration.registry.MetricId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlRowSetResultSetExtractor;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class MergeHostsMigration extends DataMigration {

  public static final SqlRowSetResultSetExtractor SQL_ROW_SET_RESULT_SET_EXTRACTOR =
      new SqlRowSetResultSetExtractor();

  private static final String INSTANCE_DUPLICATE_QUERY_WITH_ORG =
      """
        select instance_id, org_id from hosts where instance_id in (
        select instance_id from hosts group by instance_id having count(instance_id) > 1)
                                and (?::varchar is null or instance_id > ?::varchar)
                                and org_id = ?
                                and instance_id <> ''
                                and hardware_type <> ''
                                order by instance_id asc
                                limit ?;
      """;

  private static final String INSTANCE_DUPLICATE_QUERY_NO_ORG =
      """
        select instance_id, org_id from hosts where instance_id in (
        select instance_id from hosts group by instance_id having count(instance_id) > 1)
                                and (?::varchar is null or instance_id > ?::varchar)
                                and instance_id <> ''
                                and hardware_type <> ''
                                order by instance_id asc
                                limit ?;
      """;

  private HostRepository hostRepository;
  private final Counter counter;
  private String orgId;

  public MergeHostsMigration(
      JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry, HostRepository hostRepository) {
    super(jdbcTemplate, meterRegistry);
    counter = meterRegistry.counter("swatch_mergeHosts_migration");
    this.hostRepository = hostRepository;
  }

  @Override
  public SqlRowSet extract(String recordOffset, int batchSize) {
    if (orgId == null) {
      return jdbcTemplate.query(
          INSTANCE_DUPLICATE_QUERY_NO_ORG,
          new Object[] {recordOffset, recordOffset, batchSize},
          new int[] {Types.VARCHAR, Types.VARCHAR, Types.NUMERIC},
          SQL_ROW_SET_RESULT_SET_EXTRACTOR);
    } else {
      return jdbcTemplate.query(
          INSTANCE_DUPLICATE_QUERY_WITH_ORG,
          new Object[] {recordOffset, recordOffset, orgId, batchSize},
          new int[] {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC},
          SQL_ROW_SET_RESULT_SET_EXTRACTOR);
    }
  }

  public void setOrgId(String orgId) {
    this.orgId = orgId;
  }

  @Transactional
  @Override
  public String transformAndLoad(SqlRowSet data) {
    String lastInstanceId = null;
    int hostCount = 0;

    while (data.next()) {
      List<Host> deleteList = new ArrayList<>();
      String instanceId = data.getString("instance_id");
      String organizationId = data.getString("org_id");
      // get the hosts that share the id.
      List<Host> hosts =
          hostRepository
              .findAllByOrgIdAndInstanceIdIn(organizationId, Set.of(instanceId))
              .collect(Collectors.toCollection(ArrayList::new));
      Collections.sort(hosts, Comparator.comparing(Host::getLastSeen).reversed());
      Host primaryHost = hosts.get(0);
      for (Host host : hosts) {
        if (primaryHost.getId().equals(host.getId())) {
          continue;
        }
        deleteList.add(host);
        mergeDataFields(primaryHost, host);
        mergeTallyBuckets(primaryHost, host);
        mergeMeasurements(primaryHost, host);
        mergeMonthlyTotals(primaryHost, host);
      }

      lastInstanceId = instanceId;
      hostCount++;
      try {
        hostRepository.save(primaryHost);
      } catch (Exception e) {
        log.error("Unable to update host on host merge: [instanceId: {}]", e.getMessage());
        throw e;
      }
      hostRepository.deleteAll(deleteList);
    }

    counter.increment(hostCount);
    return lastInstanceId;
  }

  /**
   * Merge the identifier values into the primary (latest seen wins)
   *
   * @param primaryHost
   * @param host
   */
  private void mergeDataFields(Host primaryHost, Host host) {
    if (primaryHost.getInventoryId() == null) primaryHost.setInventoryId(host.getInventoryId());
    if (primaryHost.getInsightsId() == null) primaryHost.setInsightsId(host.getInsightsId());
    if (primaryHost.getSubscriptionManagerId() == null)
      primaryHost.setSubscriptionManagerId(host.getSubscriptionManagerId());
    if (primaryHost.getHypervisorUuid() == null)
      primaryHost.setHypervisorUuid(host.getHypervisorUuid());
    if (primaryHost.getBillingProvider() == null)
      primaryHost.setBillingProvider(host.getBillingProvider());
    if (primaryHost.getBillingAccountId() == null)
      primaryHost.setBillingAccountId(host.getBillingAccountId());
    // keep the latest applied event record date
    if (primaryHost.getLastAppliedEventRecordDate() == null
        || host.getLastAppliedEventRecordDate() != null
            && primaryHost
                    .getLastAppliedEventRecordDate()
                    .compareTo(host.getLastAppliedEventRecordDate())
                < 0) {
      primaryHost.setLastAppliedEventRecordDate(host.getLastAppliedEventRecordDate());
    }
    // HBI_HOST will always win if there are different values
    if (primaryHost.getInstanceType() == null
        || !primaryHost.getInstanceType().equals("HBI_HOST") && host.getInstanceType() != null) {
      primaryHost.setInstanceType(host.getInstanceType());
    }
  }

  /**
   * Compile all buckets from all hosts
   *
   * @param primaryHost
   * @param host
   */
  private void mergeTallyBuckets(Host primaryHost, Host host) {
    for (HostTallyBucket hostTallyBucket : host.getBuckets()) {
      primaryHost.addBucket(
          hostTallyBucket.getKey().getProductId(),
          hostTallyBucket.getKey().getSla(),
          hostTallyBucket.getKey().getUsage(),
          hostTallyBucket.getKey().getBillingProvider(),
          hostTallyBucket.getKey().getBillingAccountId(),
          hostTallyBucket.getKey().getAsHypervisor(),
          hostTallyBucket.getSockets(),
          hostTallyBucket.getCores(),
          hostTallyBucket.getMeasurementType());
    }
  }

  /**
   * Add measurements to primary only if it does not exist yet
   *
   * @param primaryHost
   * @param host
   */
  private void mergeMeasurements(Host primaryHost, Host host) {
    for (String measurementName : host.getMeasurements().keySet()) {
      if (primaryHost.getMeasurements().get(measurementName) == null) {
        primaryHost
            .getMeasurements()
            .put(measurementName, host.getMeasurements().get(measurementName));
      }
    }
  }

  /**
   * Add monthly totals to primary; duplicates will be handled in the Host object
   *
   * @param primaryHost
   * @param host
   */
  private void mergeMonthlyTotals(Host primaryHost, Host host) {
    // add monthly totals to primary. duplicates will be handled in the Host object
    for (InstanceMonthlyTotalKey monthlyKey : host.getMonthlyTotals().keySet()) {
      primaryHost.addToMonthlyTotal(
          monthlyKey.getMonth(),
          MetricId.fromString(monthlyKey.getMetricId()),
          host.getMonthlyTotal(
              monthlyKey.getMonth(), MetricId.fromString(monthlyKey.getMetricId())));
    }
  }

  @Override
  public void recordCompleted() {
    // intentionally left blank
  }
}
