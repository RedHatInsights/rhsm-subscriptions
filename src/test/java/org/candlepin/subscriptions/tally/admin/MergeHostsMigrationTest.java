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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.MetricId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

@ExtendWith(MockitoExtension.class)
class MergeHostsMigrationTest {

  @Mock HostRepository hostRepository;
  @Mock JdbcTemplate jdbcTemplate;
  @Mock MeterRegistry meterRegistry;

  @Test
  void transformAndLoadTwoHostsOneInstanceId() {
    when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
    MergeHostsMigration mergeHostsMigration =
        new MergeHostsMigration(jdbcTemplate, meterRegistry, hostRepository);
    SqlRowSet data = mock(SqlRowSet.class);
    when(data.next()).thenReturn(true, false);
    when(data.getString("instance_id")).thenReturn("instance-1");
    when(data.getString("org_id")).thenReturn("org-1");

    Host host1 =
        makeHost(
            "Host-1", "instance-1", "org-1", OffsetDateTime.now(), null, null, null, null, null);
    Host host2 =
        makeHost(
            "Host-2",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(2),
            null,
            null,
            null,
            null,
            null);

    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-1", Set.of("instance-1")))
        .thenReturn(Stream.of(host1, host2));
    assertEquals("instance-1", mergeHostsMigration.transformAndLoad(data));
    verify(hostRepository).save(host1);
    verify(hostRepository).deleteAll(List.of(host2));
  }

  @Test
  void transformAndLoadFourHostsTwoInstanceIds() {
    when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
    MergeHostsMigration mergeHostsMigration =
        new MergeHostsMigration(jdbcTemplate, meterRegistry, hostRepository);
    SqlRowSet data = mock(SqlRowSet.class);
    when(data.next()).thenReturn(true, true, false);
    when(data.getString("instance_id")).thenReturn("instance-1", "instance-2");
    when(data.getString("org_id")).thenReturn("org-1", "org-2");

    Host host1 =
        makeHost(
            "Host-1", "instance-1", "org-1", OffsetDateTime.now(), null, null, null, null, null);
    Host host2 =
        makeHost(
            "Host-2",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(2),
            null,
            null,
            null,
            null,
            null);
    Host host3 =
        makeHost(
            "Host-3", "instance-2", "org-2", OffsetDateTime.now(), null, null, null, null, null);
    Host host4 =
        makeHost(
            "Host-4",
            "instance-2",
            "org-2",
            OffsetDateTime.now().minusHours(2),
            null,
            null,
            null,
            null,
            null);

    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-1", Set.of("instance-1")))
        .thenReturn(Stream.of(host1, host2));
    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-2", Set.of("instance-2")))
        .thenReturn(Stream.of(host3, host4));
    assertEquals("instance-2", mergeHostsMigration.transformAndLoad(data));
    verify(hostRepository).save(host3);
    verify(hostRepository).deleteAll(List.of(host4));
  }

  @Test
  void transformAndLoadFieldUpdates() {
    when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
    MergeHostsMigration mergeHostsMigration =
        new MergeHostsMigration(jdbcTemplate, meterRegistry, hostRepository);
    SqlRowSet data = mock(SqlRowSet.class);
    when(data.next()).thenReturn(true, false);
    when(data.getString("instance_id")).thenReturn("instance-1");
    when(data.getString("org_id")).thenReturn("org-1");

    Host host1 =
        makeHost(
            "Host-1",
            "instance-1",
            "org-1",
            OffsetDateTime.now(),
            "insight-1",
            null,
            null,
            null,
            null);
    Host host2 =
        makeHost(
            "Host-2",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(2),
            "insight-2",
            "subscription-manager-2",
            null,
            "hypervisor-2",
            null);
    Host host3 =
        makeHost(
            "Host-3",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(3),
            "insight-3",
            "subscription-manager-3",
            "inventory-3",
            null,
            null);
    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-1", Set.of("instance-1")))
        .thenReturn(Stream.of(host1, host2, host3));

    assertEquals("instance-1", mergeHostsMigration.transformAndLoad(data));
    verify(hostRepository).save(host1);
    verify(hostRepository).deleteAll(List.of(host2, host3));
    assertEquals("insight-1", host1.getInsightsId());
    assertEquals("subscription-manager-2", host1.getSubscriptionManagerId());
    assertEquals("inventory-3", host1.getInventoryId());
    assertEquals("hypervisor-2", host1.getHypervisorUuid());
  }

  @Test
  void transformAndLoadInstanceTypeUpdates() {
    when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
    MergeHostsMigration mergeHostsMigration =
        new MergeHostsMigration(jdbcTemplate, meterRegistry, hostRepository);
    SqlRowSet data = mock(SqlRowSet.class);
    when(data.next()).thenReturn(true, false);
    when(data.getString("instance_id")).thenReturn("instance-1");
    when(data.getString("org_id")).thenReturn("org-1");

    Host host1 =
        makeHost(
            "Host-1",
            "instance-1",
            "org-1",
            OffsetDateTime.now(),
            null,
            null,
            null,
            null,
            "RHEL System");
    Host host2 =
        makeHost(
            "Host-2",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(2),
            null,
            null,
            null,
            null,
            "HBI_HOST");
    Host host3 =
        makeHost(
            "Host-3",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(3),
            null,
            null,
            null,
            null,
            null);
    Host host4 =
        makeHost(
            "Host-4",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(4),
            null,
            null,
            null,
            null,
            "RHEL System");
    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-1", Set.of("instance-1")))
        .thenReturn(Stream.of(host1, host2, host3, host4));

    assertEquals("instance-1", mergeHostsMigration.transformAndLoad(data));
    verify(hostRepository).save(host1);
    verify(hostRepository).deleteAll(List.of(host2, host3, host4));
    assertEquals("HBI_HOST", host1.getInstanceType());
  }

  @Test
  void transformAndLoadDiffentTallyBucketUpdates() {
    when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
    MergeHostsMigration mergeHostsMigration =
        new MergeHostsMigration(jdbcTemplate, meterRegistry, hostRepository);
    SqlRowSet data = mock(SqlRowSet.class);
    when(data.next()).thenReturn(true, false);
    when(data.getString("instance_id")).thenReturn("instance-1");
    when(data.getString("org_id")).thenReturn("org-1");

    Host host1 =
        makeHost(
            "Host-1", "instance-1", "org-1", OffsetDateTime.now(), null, null, null, null, null);
    HostTallyBucket bucket1 =
        makeTallyBucket(
            "product-1",
            ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "billing_account-1",
            false,
            1,
            2);
    host1.addBucket(bucket1);
    Host host2 =
        makeHost(
            "Host-2",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(2),
            null,
            null,
            null,
            null,
            null);
    HostTallyBucket bucket2 =
        makeTallyBucket(
            "product-2",
            ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "billing_account-1",
            false,
            1,
            2);
    host1.addBucket(bucket2);
    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-1", Set.of("instance-1")))
        .thenReturn(Stream.of(host1, host2));

    assertEquals("instance-1", mergeHostsMigration.transformAndLoad(data));
    assertEquals(2, host1.getBuckets().size());
    assertTrue(host1.getBuckets().containsAll(Set.of(bucket1, bucket2)));
  }

  @Test
  void transformAndLoadMeasurementUpdate() {
    when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
    MergeHostsMigration mergeHostsMigration =
        new MergeHostsMigration(jdbcTemplate, meterRegistry, hostRepository);
    SqlRowSet data = mock(SqlRowSet.class);
    when(data.next()).thenReturn(true, false);
    when(data.getString("instance_id")).thenReturn("instance-1");
    when(data.getString("org_id")).thenReturn("org-1");

    Host host1 =
        makeHost(
            "Host-1", "instance-1", "org-1", OffsetDateTime.now(), null, null, null, null, null);
    host1.setMeasurement("SOCKETS", 7.0);
    Host host2 =
        makeHost(
            "Host-2",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(2),
            null,
            null,
            null,
            null,
            null);
    host2.setMeasurement("SOCKETS", 8.0);
    host2.setMeasurement("CORES", 3.0);

    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-1", Set.of("instance-1")))
        .thenReturn(Stream.of(host1, host2));
    assertEquals("instance-1", mergeHostsMigration.transformAndLoad(data));
    assertEquals(2, host1.getMeasurements().size());
    assertEquals(7.0, host1.getMeasurements().get("SOCKETS"));
    assertEquals(3.0, host1.getMeasurements().get("CORES"));
  }

  @Test
  void transformAndLoadMonthlyTotalUpdate() {
    when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
    MergeHostsMigration mergeHostsMigration =
        new MergeHostsMigration(jdbcTemplate, meterRegistry, hostRepository);
    SqlRowSet data = mock(SqlRowSet.class);
    when(data.next()).thenReturn(true, false);
    when(data.getString("instance_id")).thenReturn("instance-1");
    when(data.getString("org_id")).thenReturn("org-1");

    Host host1 =
        makeHost(
            "Host-1", "instance-1", "org-1", OffsetDateTime.now(), null, null, null, null, null);
    host1.addToMonthlyTotal("2023-11", MetricId.fromString("CORES"), 80.0);
    host1.addToMonthlyTotal("2023-10", MetricId.fromString("CORES"), 100.0);
    host1.addToMonthlyTotal("2023-10", MetricId.fromString("VCPUS"), 50.0);
    Host host2 =
        makeHost(
            "Host-2",
            "instance-1",
            "org-1",
            OffsetDateTime.now().minusHours(2),
            null,
            null,
            null,
            null,
            null);
    host2.addToMonthlyTotal("2023-11", MetricId.fromString("CORES"), 10.0);
    host2.addToMonthlyTotal("2023-10", MetricId.fromString("CORES"), 5.0);
    host2.addToMonthlyTotal("2023-10", MetricId.fromString("SOCKETS"), 25.0);

    when(hostRepository.findAllByOrgIdAndInstanceIdIn("org-1", Set.of("instance-1")))
        .thenReturn(Stream.of(host1, host2));
    assertEquals("instance-1", mergeHostsMigration.transformAndLoad(data));
    assertEquals(4, host1.getMonthlyTotals().size());
    assertEquals(90.0, host1.getMonthlyTotal("2023-11", MetricId.fromString("CORES")));
    assertEquals(105.0, host1.getMonthlyTotal("2023-10", MetricId.fromString("CORES")));
    assertEquals(25.0, host1.getMonthlyTotal("2023-10", MetricId.fromString("SOCKETS")));
    assertEquals(50.0, host1.getMonthlyTotal("2023-10", MetricId.fromString("VCPUS")));
  }

  private Host makeHost(
      String displayName,
      String instanceId,
      String orgId,
      OffsetDateTime lastSeen,
      String insightsId,
      String subscriptionManagerId,
      String inventoryId,
      String hypervisorUuid,
      String instanceType) {
    Host host = new Host();
    host.setId(UUID.randomUUID());
    host.setDisplayName(displayName);
    host.setInstanceId(instanceId);
    host.setOrgId(orgId);
    host.setLastSeen(lastSeen);
    host.setInsightsId(insightsId);
    host.setSubscriptionManagerId(subscriptionManagerId);
    host.setInventoryId(inventoryId);
    host.setHypervisorUuid(hypervisorUuid);
    host.setInstanceType(instanceType);
    return host;
  }

  private HostTallyBucket makeTallyBucket(
      String productTag,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      boolean asHypervisor,
      int cores,
      int sockets) {
    HostTallyBucket bucket = new HostTallyBucket();
    HostBucketKey key = new HostBucketKey();
    key.setProductId(productTag);
    key.setSla(sla);
    key.setUsage(usage);
    key.setBillingProvider(billingProvider);
    key.setBillingAccountId(billingAccountId);
    key.setAsHypervisor(asHypervisor);
    bucket.setCores(cores);
    bucket.setSockets(sockets);
    bucket.setKey(key);
    return bucket;
  }
}
