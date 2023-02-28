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
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.resource.HostsResource;
import org.candlepin.subscriptions.resource.InstancesResource;
import org.candlepin.subscriptions.utilization.api.model.HostReportSort;
import org.candlepin.subscriptions.utilization.api.model.InstanceReportSort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(Lifecycle.PER_CLASS)
class HostRepositoryTest {

  private final String RHEL = "RHEL";
  private final String COOL_PROD = "COOL_PROD";
  private static final String DEFAULT_DISPLAY_NAME = "REDHAT_PWNS";
  private static final String SANITIZED_MISSING_DISPLAY_NAME = "";

  @Autowired private HostRepository repo;
  @Autowired private AccountServiceInventoryRepository accountServiceInventoryRepository;

  private Map<String, Host> existingHostsByInventoryId;

  @Transactional
  @BeforeAll
  void setupTestData() {

    // ACCOUNT 1 HOSTS
    Host host1 = createHost("inventory1", "account1");
    addBucketToHost(host1, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    // ACCOUNT 2 HOSTS
    Host host2 = createHost("inventory2", "account2");
    addBucketToHost(host2, COOL_PROD, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    Host host3 = createHost("inventory3", "account2");
    addBucketToHost(host3, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    Host host4 = createHost("inventory4", "account2");
    addBucketToHost(host4, RHEL, ServiceLevel.SELF_SUPPORT, Usage.PRODUCTION);

    Host host5 = createHost("inventory5", "account2");
    addBucketToHost(host5, RHEL, ServiceLevel.SELF_SUPPORT, Usage.DISASTER_RECOVERY);

    // ACCOUNT 3 HOSTS
    Host host6 = createHost("inventory6", "account3");

    Host host7 = createHost("inventory7", "account3");
    addBucketToHost(
        host7, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, HardwareMeasurementType.VIRTUAL);

    Host host8 = createHost("inventory8", "account123");
    Host host9 = createHost("inventory9", "account123");
    Host host10 = createHost("inventory10", "account123");

    for (Uom uom : Uom.values()) {
      host8.addToMonthlyTotal(
          OffsetDateTime.of(LocalDateTime.of(2021, 1, 1, 0, 0, 0), ZoneOffset.UTC), uom, 100.0);
      host9.addToMonthlyTotal(
          OffsetDateTime.of(LocalDateTime.of(2021, 1, 1, 0, 0, 0), ZoneOffset.UTC), uom, 0.0);
      host10.addToMonthlyTotal(
          OffsetDateTime.of(LocalDateTime.of(2021, 2, 1, 0, 0, 0), ZoneOffset.UTC), uom, 50.0);
    }

    addBucketToHost(host8, RHEL, ServiceLevel._ANY, Usage._ANY, HardwareMeasurementType.PHYSICAL);
    addBucketToHost(host9, RHEL, ServiceLevel._ANY, Usage._ANY, HardwareMeasurementType.PHYSICAL);
    addBucketToHost(host10, RHEL, ServiceLevel._ANY, Usage._ANY, HardwareMeasurementType.PHYSICAL);

    existingHostsByInventoryId =
        persistHosts(host1, host2, host3, host4, host5, host6, host7, host8, host9, host10).stream()
            .collect(Collectors.toMap(Host::getInventoryId, Function.identity()));
  }

  @Transactional
  @AfterAll
  void cleanup() {
    accountServiceInventoryRepository.deleteAll();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  private List<Host> persistHosts(Host... hosts) {
    List<Host> toSave = Arrays.asList(hosts);
    toSave.stream()
        .filter(h -> h.getDisplayName() == null)
        .forEach(x -> x.setDisplayName(DEFAULT_DISPLAY_NAME));
    List<Host> results = new ArrayList<>();
    Arrays.stream(hosts)
        .forEach(
            host -> {
              AccountServiceInventory accountServiceInventory =
                  accountServiceInventoryRepository
                      .findById(
                          AccountServiceInventoryId.builder()
                              .orgId(host.getOrgId())
                              .serviceType("HBI_HOST")
                              .build())
                      .orElse(new AccountServiceInventory(host.getOrgId(), "HBI_HOST"));
              accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host);
              accountServiceInventory =
                  accountServiceInventoryRepository.save(accountServiceInventory);
              results.add(accountServiceInventory.getServiceInstances().get(host.getInstanceId()));
            });
    accountServiceInventoryRepository.flush();
    return results;
  }

  @Test
  void testTallyHostViewProjection() {
    // Ensure that the TallyHostView is properly projecting values.
    OffsetDateTime expLastSeen = OffsetDateTime.now();
    String expInventoryId = "INV";
    String expInsightsId = "INSIGHTS_ID";
    String expAccount = "ACCT";
    String expOrg = "ORG";
    String expSubId = "SUB_ID";
    String expDisplayName = "HOST_DISPLAY";
    HardwareMeasurementType expMeasurementType = HardwareMeasurementType.GOOGLE;
    HostHardwareType expHardwareType = HostHardwareType.PHYSICAL;
    int expCores = 8;
    int expSockets = 4;
    int expGuests = 10;
    boolean expUnmappedGuest = true;
    boolean expIsHypervisor = true;
    String expCloudProvider = "CLOUD_PROVIDER";

    Host host = new Host(expInventoryId, expInsightsId, expAccount, expOrg, expSubId);
    host.setBillingProvider(BillingProvider.RED_HAT);
    host.setBillingAccountId("_ANY");
    host.setNumOfGuests(expGuests);
    host.setDisplayName(expDisplayName);
    host.setLastSeen(expLastSeen);
    host.setMeasurement(Uom.CORES, 12.0);
    host.setMeasurement(Uom.SOCKETS, 12.0);
    host.setHypervisor(expIsHypervisor);
    host.setUnmappedGuest(expUnmappedGuest);
    host.setCloudProvider(expCloudProvider);
    host.setHardwareType(expHardwareType);

    host.addBucket(
        RHEL,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        false,
        expSockets,
        expCores,
        expMeasurementType);

    persistHosts(host);

    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            expOrg,
            RHEL,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());
    assertEquals(1, found.size());

    TallyHostView view = found.get(0);
    assertEquals(expInventoryId, view.getInventoryId());
    assertEquals(expInsightsId, view.getInsightsId());
    assertEquals(expDisplayName, view.getDisplayName());
    assertEquals(expMeasurementType.name(), view.getHardwareMeasurementType());
    assertEquals(expHardwareType.name(), view.getHardwareType());
    assertEquals(expCores, view.getCores());
    assertEquals(expSockets, view.getSockets());
    assertEquals(expGuests, view.getNumberOfGuests().intValue());
    assertEquals(expSubId, view.getSubscriptionManagerId());
    assertEquals(
        expLastSeen.format(DateTimeFormatter.BASIC_ISO_DATE),
        view.getLastSeen().format(DateTimeFormatter.BASIC_ISO_DATE));
    assertEquals(expUnmappedGuest, view.isUnmappedGuest());
    assertEquals(expIsHypervisor, view.isHypervisor());
    assertEquals(expCloudProvider, view.getCloudProvider());
  }

  @Transactional
  @Test
  void testCreate() {
    Host host = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
    host.setDisplayName(DEFAULT_DISPLAY_NAME);
    host.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        false,
        4,
        2,
        HardwareMeasurementType.PHYSICAL);
    host.setMeasurement(Measurement.Uom.CORES, 4.0);
    host.addToMonthlyTotal(
        OffsetDateTime.parse("2021-02-26T01:00:00Z"), Measurement.Uom.CORES, 5.0);
    host = persistHosts(host).get(0);

    Optional<Host> result = repo.findById(host.getId());
    assertTrue(result.isPresent());
    Host saved = result.get();
    assertEquals(1, saved.getBuckets().size());
    assertEquals(4.0, saved.getMeasurement(Measurement.Uom.CORES));
    assertEquals(5.0, saved.getMonthlyTotal("2021-02", Measurement.Uom.CORES));
  }

  @Transactional
  @Test
  void testUpdate() {
    Host host = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
    host.setDisplayName(DEFAULT_DISPLAY_NAME);
    host.setMeasurement(Uom.SOCKETS, 1.0);
    host.setMeasurement(Uom.CORES, 1.0);
    host.setMeasurement(Measurement.Uom.CORES, 2.0);
    host.addToMonthlyTotal(
        OffsetDateTime.parse("2021-02-26T01:00:00Z"), Measurement.Uom.CORES, 3.0);
    host.addToMonthlyTotal(
        OffsetDateTime.parse("2021-01-26T01:00:00Z"), Measurement.Uom.SOCKETS, 10.0);

    host.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        false,
        4,
        2,
        HardwareMeasurementType.PHYSICAL);
    host.addBucket(
        "Satellite",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        true,
        4,
        2,
        HardwareMeasurementType.PHYSICAL);
    host = persistHosts(host).get(0);

    Optional<Host> result = repo.findById(host.getId());
    assertTrue(result.isPresent());
    Host toUpdate = result.get();
    assertEquals(2, toUpdate.getBuckets().size());

    toUpdate.setAccountNumber("updated_acct_num");
    toUpdate.setMeasurement(Uom.SOCKETS, 4.0);
    toUpdate.setMeasurement(Uom.CORES, 8.0);
    toUpdate.setDisplayName(DEFAULT_DISPLAY_NAME);

    HostTallyBucket rhelBucket =
        host.getBuckets().stream()
            .filter(h -> h.getKey().getProductId().equals("RHEL"))
            .findFirst()
            .orElse(null);
    HostTallyBucket satelliteBucket =
        host.getBuckets().stream()
            .filter(h -> h.getKey().getProductId().equals("Satellite"))
            .findFirst()
            .orElse(null);
    toUpdate.removeBucket(rhelBucket);
    toUpdate.setMeasurement(Measurement.Uom.CORES, 8.0);
    toUpdate.addToMonthlyTotal(
        OffsetDateTime.parse("2021-02-26T01:00:00Z"), Measurement.Uom.CORES, 4.0);
    toUpdate.clearMonthlyTotal(OffsetDateTime.parse("2021-01-02T00:00:00Z"));
    persistHosts(toUpdate);

    Optional<Host> updateResult = repo.findById(toUpdate.getId());
    assertTrue(updateResult.isPresent());
    Host updated = updateResult.get();
    assertEquals("updated_acct_num", updated.getAccountNumber());
    assertEquals(4, updated.getMeasurement(Uom.SOCKETS).intValue());
    assertEquals(8, updated.getMeasurement(Uom.CORES).intValue());
    assertEquals(1, updated.getBuckets().size());
    assertTrue(updated.getBuckets().contains(satelliteBucket));
    assertEquals(8.0, updated.getMeasurement(Measurement.Uom.CORES));
    assertEquals(7.0, updated.getMonthlyTotal("2021-02", Measurement.Uom.CORES));
    assertNull(updated.getMonthlyTotal("2021-02", Measurement.Uom.SOCKETS));
  }

  @Transactional
  @Test
  void testFindHostsWhenAccountIsDifferent() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account1",
            RHEL,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory1");
  }

  @Transactional
  @Test
  void testFindHostsWhenProductIsDifferent() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            COOL_PROD,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory2");
  }

  @Transactional
  @Test
  void testFindHostsWhenSLAIsDifferent() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            RHEL,
            ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory4");
  }

  @Transactional
  @Test
  void testFindHostsWhenUsageIsDifferent() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            RHEL,
            ServiceLevel.SELF_SUPPORT,
            Usage.DISASTER_RECOVERY,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory5");
  }

  @Transactional
  @Test
  void testNoHostFoundWhenItHasNoBucket() {
    Optional<Host> existing = repo.findById(existingHostsByInventoryId.get("inventory6").getId());
    assertTrue(existing.isPresent());
    assertEquals("account3", existing.get().getAccountNumber());

    // When a host has no buckets, it will not be returned.
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account4", null, null, null, null, null, null, 0, 0, PageRequest.of(0, 10));
    assertEquals(0, hosts.stream().count());
  }

  @Transactional
  @Test
  void testReturnsGuestsOfHypervisor() {
    String account = "hostGuestTest";
    String uuid = UUID.randomUUID().toString();

    Host hypervisor = createHost("hypervisor", account);
    hypervisor.setSubscriptionManagerId(uuid);
    addBucketToHost(hypervisor, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION);

    Host guest = createHost("guest", account);
    guest.setGuest(true);
    guest.setHypervisorUuid(uuid);
    addBucketToHost(guest, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION);

    Host unmappedGuest = createHost("unmappedGuest", account);
    unmappedGuest.setGuest(true);
    addBucketToHost(unmappedGuest, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION);

    List<Host> toSave = Arrays.asList(hypervisor, guest, unmappedGuest);
    toSave.forEach(x -> x.setDisplayName(DEFAULT_DISPLAY_NAME));
    persistHosts(toSave.toArray(new Host[] {}));

    Page<Host> guests = repo.getHostsByHypervisor("ORG_" + account, uuid, PageRequest.of(0, 10));
    assertEquals(1, guests.getTotalElements());
    assertEquals(uuid, guests.getContent().get(0).getHypervisorUuid());
    assertEquals("guest", guests.getContent().get(0).getInventoryId());
    assertEquals("INSIGHTS_guest", guests.getContent().get(0).getInsightsId());
  }

  @Transactional
  @Test
  void testCanSortByIdForImplicitSort() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10, Sort.Direction.ASC, "id"));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory3");
  }

  @Transactional
  @Test
  void testCanSortByDisplayName() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                10,
                Sort.Direction.ASC,
                HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory3");
  }

  @Transactional
  @Test
  void testCanSortByMeasurementType() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account3",
            RHEL,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                10,
                Sort.Direction.ASC,
                HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory7");
  }

  @Transactional
  @Test
  void testCanSortByCores() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                10,
                Sort.Direction.ASC,
                HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.CORES)));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory3");
  }

  @Transactional
  @Test
  void testCanSortBySockets() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                10,
                Sort.Direction.ASC,
                HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.SOCKETS)));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory3");
  }

  @Transactional
  @Test
  void testCanSortByLastSeen() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                10,
                Sort.Direction.ASC,
                HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.LAST_SEEN)));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory3");
  }

  @Transactional
  @Test
  void testCanSortByHardwareType() {
    Page<TallyHostView> hosts =
        repo.getTallyHostViews(
            "ORG_account2",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(
                0,
                10,
                Sort.Direction.ASC,
                HostsResource.HOST_SORT_PARAM_MAPPING.get(HostReportSort.HARDWARE_TYPE)));
    List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

    assertEquals(1, found.size());
    assertTallyHostView(found.get(0), "inventory3");
  }

  @Transactional
  @Test
  void testGetHostViews() {
    Host host1 = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
    host1.setMeasurement(Uom.SOCKETS, 1.0);
    host1.setMeasurement(Uom.CORES, 1.0);
    host1.setNumOfGuests(4);

    host1.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        true,
        4,
        2,
        HardwareMeasurementType.PHYSICAL);
    host1.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        false,
        10,
        5,
        HardwareMeasurementType.VIRTUAL);
    host1.addBucket(
        "Satellite",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        true,
        4,
        2,
        HardwareMeasurementType.PHYSICAL);

    List<Host> toPersist =
        Arrays.asList(
            host1,
            new Host("INV2", "HOST2", "my_acct", "my_org", "sub2_id"),
            new Host("INV3", "HOST3", "my_acct2", "another_org", "sub3_id"));

    toPersist.forEach(x -> x.setDisplayName(DEFAULT_DISPLAY_NAME));
    persistHosts(toPersist.toArray(new Host[] {}));

    Page<TallyHostView> results =
        repo.getTallyHostViews(
            "my_org",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10));
    Map<String, TallyHostView> hosts =
        results.getContent().stream()
            .collect(
                Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
    assertEquals(2, hosts.size());

    assertTrue(hosts.containsKey(HardwareMeasurementType.PHYSICAL.toString()));
    TallyHostView physical = hosts.get(HardwareMeasurementType.PHYSICAL.toString());
    assertEquals(host1.getInventoryId(), physical.getInventoryId());
    assertEquals(host1.getSubscriptionManagerId(), physical.getSubscriptionManagerId());
    assertEquals(host1.getNumOfGuests(), physical.getNumberOfGuests());
    assertEquals(4, physical.getSockets());
    assertEquals(2, physical.getCores());

    assertTrue(hosts.containsKey(HardwareMeasurementType.VIRTUAL.toString()));
    TallyHostView hypervisor = hosts.get(HardwareMeasurementType.VIRTUAL.toString());
    assertEquals(host1.getInventoryId(), hypervisor.getInventoryId());
    assertEquals(host1.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    assertEquals(host1.getNumOfGuests(), hypervisor.getNumberOfGuests());
    assertEquals(10, hypervisor.getSockets());
    assertEquals(5, hypervisor.getCores());
  }

  static InstanceReportSort[] instanceSortParams() {
    return InstancesResource.INSTANCE_SORT_PARAM_MAPPING
        .keySet()
        .toArray(new InstanceReportSort[0]);
  }

  static String[] tallyViewSortParams() {
    return HostsResource.HOST_SORT_PARAM_MAPPING.values().toArray(new String[0]);
  }

  @Transactional
  @ParameterizedTest
  @MethodSource("org.candlepin.subscriptions.db.HostRepositoryTest#tallyViewSortParams")
  void canSortByHostBasedSortMethods(String sort) {
    Pageable page = PageRequest.of(0, 1, Sort.by(sort));
    assertNotNull(
        repo.getTallyHostViews(
            "ORG_account1234",
            "product",
            ServiceLevel._ANY,
            Usage._ANY,
            null,
            null,
            "",
            1,
            0,
            page));
  }

  @Transactional
  @Test
  void testNullNumGuests() {
    Host host = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
    host.setDisplayName(DEFAULT_DISPLAY_NAME);
    host.setMeasurement(Uom.SOCKETS, 1.0);
    host.setMeasurement(Uom.CORES, 1.0);

    host.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        "_ANY",
        true,
        4,
        2,
        HardwareMeasurementType.PHYSICAL);

    List<Host> toPersist = Collections.singletonList(host);
    persistHosts(toPersist.toArray(new Host[] {}));

    Page<TallyHostView> results =
        repo.getTallyHostViews(
            "my_org",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            0,
            PageRequest.of(0, 10));
    Map<String, TallyHostView> hosts =
        results.getContent().stream()
            .collect(
                Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
    assertEquals(1, hosts.size());

    assertTrue(hosts.containsKey(HardwareMeasurementType.PHYSICAL.toString()));
    TallyHostView physical = hosts.get(HardwareMeasurementType.PHYSICAL.toString());
    assertEquals(host.getInventoryId(), physical.getInventoryId());
    assertEquals(host.getSubscriptionManagerId(), physical.getSubscriptionManagerId());
    assertNull(physical.getNumberOfGuests());
    assertEquals(4, physical.getSockets());
    assertEquals(2, physical.getCores());
  }

  @Transactional
  @Test
  void testShouldFilterSockets() {
    Host coreHost = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
    coreHost.setDisplayName(DEFAULT_DISPLAY_NAME);
    coreHost.setMeasurement(Uom.SOCKETS, 0.0);
    coreHost.setMeasurement(Uom.CORES, 1.0);
    coreHost.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        true,
        0,
        1,
        HardwareMeasurementType.PHYSICAL);

    Host socketHost = new Host("INV2", "HOST2", "my_acct", "my_org", "sub_id");
    socketHost.setDisplayName(DEFAULT_DISPLAY_NAME);
    socketHost.setMeasurement(Uom.SOCKETS, 1.0);
    socketHost.setMeasurement(Uom.CORES, 0.0);
    socketHost.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        true,
        1,
        0,
        HardwareMeasurementType.PHYSICAL);

    List<Host> toPersist = Arrays.asList(coreHost, socketHost);
    persistHosts(toPersist.toArray(new Host[] {}));

    Page<TallyHostView> results =
        repo.getTallyHostViews(
            "my_org",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            0,
            1,
            PageRequest.of(0, 10));
    Map<String, TallyHostView> hosts =
        results.getContent().stream()
            .collect(
                Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
    assertEquals(1, hosts.size());

    assertTrue(hosts.containsKey(HardwareMeasurementType.PHYSICAL.toString()));
    TallyHostView physical = hosts.get(HardwareMeasurementType.PHYSICAL.toString());
    assertEquals(socketHost.getInventoryId(), physical.getInventoryId());
    assertEquals(socketHost.getSubscriptionManagerId(), physical.getSubscriptionManagerId());
    assertEquals(1, physical.getSockets());
    assertEquals(0, physical.getCores());
  }

  @Transactional
  @Test
  void testShouldFilterCores() {
    Host coreHost = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
    coreHost.setDisplayName(DEFAULT_DISPLAY_NAME);
    coreHost.setMeasurement(Uom.SOCKETS, 0.0);
    coreHost.setMeasurement(Uom.CORES, 1.0);
    coreHost.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        true,
        0,
        1,
        HardwareMeasurementType.PHYSICAL);

    Host socketHost = new Host("INV2", "HOST2", "my_acct", "my_org", "sub_id");
    socketHost.setDisplayName(DEFAULT_DISPLAY_NAME);
    socketHost.setMeasurement(Uom.SOCKETS, 1.0);
    socketHost.setMeasurement(Uom.CORES, 0.0);
    socketHost.addBucket(
        "RHEL",
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        BillingProvider.RED_HAT,
        "_ANY",
        true,
        1,
        0,
        HardwareMeasurementType.PHYSICAL);

    List<Host> toPersist = Arrays.asList(coreHost, socketHost);
    persistHosts(toPersist.toArray(new Host[] {}));

    Page<TallyHostView> results =
        repo.getTallyHostViews(
            "my_org",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            "_ANY",
            SANITIZED_MISSING_DISPLAY_NAME,
            1,
            0,
            PageRequest.of(0, 10));
    Map<String, TallyHostView> hosts =
        results.getContent().stream()
            .collect(
                Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
    assertEquals(1, hosts.size());

    assertTrue(hosts.containsKey(HardwareMeasurementType.PHYSICAL.toString()));
    TallyHostView physical = hosts.get(HardwareMeasurementType.PHYSICAL.toString());
    assertEquals(coreHost.getInventoryId(), physical.getInventoryId());
    assertEquals(coreHost.getSubscriptionManagerId(), physical.getSubscriptionManagerId());
    assertEquals(0, physical.getSockets());
    assertEquals(1, physical.getCores());
  }

  @Transactional
  @Test
  void testFilterByBillingModel() {
    Host host1 = createHost("i1", "a1");
    host1.setBillingProvider(BillingProvider.RED_HAT);
    addBucketToHost(
        host1,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider.RED_HAT);
    addBucketToHost(
        host1,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider._ANY);

    Host host2 = createHost("i2", "a1");
    host2.setBillingProvider(BillingProvider.AWS);
    addBucketToHost(
        host2,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider.AWS);
    addBucketToHost(
        host2,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider._ANY);

    Host host3 = createHost("i3", "a1");
    addBucketToHost(
        host3,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider.EMPTY);
    addBucketToHost(
        host3,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider._ANY);

    persistHosts(host1, host2, host3);

    HostReportSort sort = HostReportSort.CORES;
    String sortValue = HostsResource.INSTANCE_SORT_PARAM_MAPPING.get(sort);
    Pageable page = PageRequest.of(0, 10, Sort.by(sortValue));

    Page<Host> results =
        repo.findAllBy(
            "ORG_a1",
            COOL_PROD,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            "",
            0,
            0,
            null,
            null,
            BillingProvider.AWS,
            "_ANY",
            null,
            page);
    assertEquals(1L, results.getTotalElements());
    assertEquals(BillingProvider.AWS, results.getContent().get(0).getBillingProvider());

    Page<Host> allResults =
        repo.findAllBy(
            "ORG_a1",
            COOL_PROD,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            "",
            0,
            0,
            null,
            null,
            BillingProvider._ANY,
            "_ANY",
            null,
            page);
    assertEquals(3L, allResults.getTotalElements());
    Map<String, Host> hostToBill =
        allResults.stream().collect(Collectors.toMap(Host::getInstanceId, Function.identity()));
    assertTrue(
        hostToBill.keySet().containsAll(Arrays.asList("i1", "i2", "i3")),
        "Result did not contain expected hosts!");
    assertEquals(BillingProvider.RED_HAT, hostToBill.get("i1").getBillingProvider());
    assertEquals(BillingProvider.AWS, hostToBill.get("i2").getBillingProvider());
    assertNull(hostToBill.get("i3").getBillingProvider());
  }

  // TODO More tests
  @Transactional
  @ParameterizedTest
  @CsvSource({"'',3", "banana,1", "rang,1", "an,2", "celery,0"})
  void testDisplayNameFilter(String displayNameSubstring, Integer expectedResults) {

    String inventoryId = "INV";
    String acctNumber = "ACCT";

    Host host0 = createHost(inventoryId, acctNumber);
    host0.setDisplayName("oranges");
    addBucketToHost(host0, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    Host host1 = createHost(inventoryId, acctNumber);
    host1.setDisplayName("kiwi");
    addBucketToHost(host1, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    Host host2 = createHost(inventoryId, acctNumber);
    host2.setDisplayName("banana");
    addBucketToHost(host2, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION);

    List<Host> toPersist = Arrays.asList(host0, host1, host2);
    persistHosts(toPersist.toArray(new Host[] {}));

    int sockets = 4;
    int cores = 2;

    Page<TallyHostView> results =
        repo.getTallyHostViews(
            "ORG_ACCT",
            RHEL,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            displayNameSubstring,
            cores,
            sockets,
            null);

    int expected = expectedResults;
    int actual = results.getContent().size();

    assertEquals(expected, actual);
  }

  @Transactional
  @Test
  void testFilterByHardwareMeasurementTypes() {
    Host host1 = createHost("i1", "a1");
    host1.setBillingProvider(BillingProvider.RED_HAT);
    addBucketToHost(
        host1,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider.RED_HAT);

    Host host2 = createHost("i2", "a1");
    host2.setBillingProvider(BillingProvider.RED_HAT);
    addBucketToHost(
        host2,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.VIRTUAL,
        BillingProvider.RED_HAT);

    Host host3 = createHost("i3", "a1");
    addBucketToHost(
        host3,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.HYPERVISOR,
        BillingProvider.RED_HAT);

    persistHosts(host1, host2, host3);

    HostReportSort sort = HostReportSort.CORES;
    String sortValue = HostsResource.INSTANCE_SORT_PARAM_MAPPING.get(sort);
    Pageable page = PageRequest.of(0, 10, Sort.by(sortValue));

    Page<Host> results =
        repo.findAllBy(
            "ORG_a1",
            COOL_PROD,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            "",
            0,
            0,
            null,
            null,
            BillingProvider.RED_HAT,
            "_ANY",
            List.of(HardwareMeasurementType.VIRTUAL),
            page);
    assertEquals(1L, results.getTotalElements());
    assertEquals(
        HardwareMeasurementType.VIRTUAL,
        results.getContent().get(0).getBuckets().iterator().next().getMeasurementType());

    Page<Host> allResults =
        repo.findAllBy(
            "ORG_a1",
            COOL_PROD,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            "",
            0,
            0,
            null,
            null,
            BillingProvider.RED_HAT,
            "_ANY",
            null,
            page);
    assertEquals(3L, allResults.getTotalElements());
    Map<String, Host> hostToBill =
        allResults.stream().collect(Collectors.toMap(Host::getInstanceId, Function.identity()));
    assertTrue(
        hostToBill.keySet().containsAll(Arrays.asList("i1", "i2", "i3")),
        "Result did not contain expected hosts!");
    assertEquals(
        HardwareMeasurementType.PHYSICAL,
        hostToBill.get("i1").getBuckets().iterator().next().getMeasurementType());
    assertEquals(
        HardwareMeasurementType.VIRTUAL,
        hostToBill.get("i2").getBuckets().iterator().next().getMeasurementType());
    assertEquals(
        HardwareMeasurementType.HYPERVISOR,
        hostToBill.get("i3").getBuckets().iterator().next().getMeasurementType());
  }

  @Transactional
  @Test
  void testFilterByCloudHardwareMeasurementTypes() {
    Host host1 = createHost("i1", "a1");
    host1.setBillingProvider(BillingProvider.RED_HAT);
    addBucketToHost(
        host1,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.AWS,
        BillingProvider.RED_HAT);

    Host host2 = createHost("i2", "a1");
    host2.setBillingProvider(BillingProvider.RED_HAT);
    addBucketToHost(
        host2,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.AZURE,
        BillingProvider.RED_HAT);

    Host host3 = createHost("i3", "a1");
    addBucketToHost(
        host3,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.GOOGLE,
        BillingProvider.RED_HAT);

    Host host4 = createHost("i4", "a1");
    addBucketToHost(
        host4,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.AWS_CLOUDIGRADE,
        BillingProvider.RED_HAT);

    Host host5 = createHost("i5", "a1");
    addBucketToHost(
        host5,
        COOL_PROD,
        ServiceLevel.PREMIUM,
        Usage.PRODUCTION,
        HardwareMeasurementType.PHYSICAL,
        BillingProvider.RED_HAT);

    persistHosts(host1, host2, host3, host4, host5);

    HostReportSort sort = HostReportSort.CORES;
    String sortValue = HostsResource.INSTANCE_SORT_PARAM_MAPPING.get(sort);
    Pageable page = PageRequest.of(0, 10, Sort.by(sortValue));

    Page<Host> results =
        repo.findAllBy(
            "ORG_a1",
            COOL_PROD,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            "",
            0,
            0,
            null,
            null,
            BillingProvider.RED_HAT,
            "_ANY",
            HardwareMeasurementType.getCloudProviderTypes(),
            page);
    assertEquals(4L, results.getTotalElements());
    Map<String, Host> hostToBill =
        results.stream().collect(Collectors.toMap(Host::getInstanceId, Function.identity()));
    assertTrue(hostToBill.keySet().containsAll(Arrays.asList("i1", "i2", "i3", "i4")));
  }

  private Host createHost(String inventoryId, String account) {
    Host host =
        new Host(
            inventoryId,
            "INSIGHTS_" + inventoryId,
            account,
            "ORG_" + account,
            "SUBMAN_" + inventoryId);
    host.setMeasurement(Uom.SOCKETS, 1.0);
    host.setMeasurement(Uom.CORES, 1.0);
    return host;
  }

  private HostTallyBucket addBucketToHost(
      Host host, String productId, ServiceLevel sla, Usage usage) {
    return addBucketToHost(host, productId, sla, usage, HardwareMeasurementType.PHYSICAL);
  }

  private HostTallyBucket addBucketToHost(
      Host host,
      String productId,
      ServiceLevel sla,
      Usage usage,
      HardwareMeasurementType measurementType) {
    return addBucketToHost(
        host, productId, sla, usage, measurementType, BillingProvider._ANY, "_ANY");
  }

  private HostTallyBucket addBucketToHost(
      Host host,
      String productId,
      ServiceLevel sla,
      Usage usage,
      HardwareMeasurementType measurementType,
      BillingProvider billingProvider) {
    return addBucketToHost(host, productId, sla, usage, measurementType, billingProvider, "_ANY");
  }

  private HostTallyBucket addBucketToHost(
      Host host,
      String productId,
      ServiceLevel sla,
      Usage usage,
      HardwareMeasurementType measurementType,
      BillingProvider billingProvider,
      String billingAccountId) {
    return host.addBucket(
        productId, sla, usage, billingProvider, billingAccountId, true, 4, 2, measurementType);
  }

  private void assertTallyHostView(TallyHostView host, String inventoryId) {
    assertNotNull(host);
    assertEquals(inventoryId, host.getInventoryId());
    assertEquals("INSIGHTS_" + inventoryId, host.getInsightsId());
    assertEquals("SUBMAN_" + inventoryId, host.getSubscriptionManagerId());
  }
}
