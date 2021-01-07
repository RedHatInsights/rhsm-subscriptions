/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.HostsResource;
import org.candlepin.subscriptions.utilization.api.model.HostReportSort;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(Lifecycle.PER_CLASS)
class HostRepositoryTest {

    private final String RHEL = "RHEL";
    private final String COOL_PROD = "COOL_PROD";
    private static final String DEFAULT_DISPLAY_NAME = "REDHAT_PWNS";
    private static final String SANITIZED_MISSING_DISPLAY_NAME = "";

    @Autowired
    private HostRepository repo;

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
        addBucketToHost(host7, RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, HardwareMeasurementType.VIRTUAL);

        persistHosts(host1, host2, host3, host4, host5, host6, host7);
    }

    private void persistHosts(Host ... hosts) {
        List<Host> toSave = Arrays.asList(hosts);
        toSave.forEach(x -> x.setDisplayName(DEFAULT_DISPLAY_NAME));
        existingHostsByInventoryId = repo.saveAll(toSave)
            .stream()
            .collect(Collectors.toMap(Host::getInventoryId, host -> host));
        repo.flush();
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
        host.setNumOfGuests(expGuests);
        host.setDisplayName(expDisplayName);
        host.setLastSeen(expLastSeen);
        host.setCores(12);
        host.setSockets(12);
        host.setHypervisor(expIsHypervisor);
        host.setUnmappedGuest(expUnmappedGuest);
        host.setCloudProvider(expCloudProvider);
        host.setHardwareType(expHardwareType);

        host.addBucket(RHEL, ServiceLevel.PREMIUM, Usage.PRODUCTION, false, expSockets, expCores,
            expMeasurementType);

        repo.saveAndFlush(host);

        Page<TallyHostView> hosts = repo.getTallyHostViews(expAccount, RHEL, ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10));
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
        assertEquals(expLastSeen.format(DateTimeFormatter.BASIC_ISO_DATE),
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
        host.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false, 4, 2,
            HardwareMeasurementType.PHYSICAL);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getId());
        assertTrue(result.isPresent());
        Host saved = result.get();
        assertEquals(1, saved.getBuckets().size());
    }

    @Transactional
    @Test
    void testUpdate() {
        Host host = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
        host.setDisplayName(DEFAULT_DISPLAY_NAME);
        host.setSockets(1);
        host.setCores(1);

        host.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false, 4, 2,
            HardwareMeasurementType.PHYSICAL);
        host.addBucket("Satellite", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 4, 2,
            HardwareMeasurementType.PHYSICAL);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getId());
        assertTrue(result.isPresent());
        Host toUpdate = result.get();
        assertEquals(2, toUpdate.getBuckets().size());

        toUpdate.setAccountNumber("updated_acct_num");
        toUpdate.setSockets(4);
        toUpdate.setCores(8);
        toUpdate.setDisplayName(DEFAULT_DISPLAY_NAME);
        toUpdate.removeBucket(host.getBuckets().get(0));
        repo.saveAndFlush(toUpdate);

        Optional<Host> updateResult = repo.findById(toUpdate.getId());
        assertTrue(updateResult.isPresent());
        Host updated = updateResult.get();
        assertEquals("updated_acct_num", updated.getAccountNumber());
        assertEquals(4, updated.getSockets().intValue());
        assertEquals(8, updated.getCores().intValue());
        assertEquals(1, updated.getBuckets().size());
        assertTrue(updated.getBuckets().contains(host.getBuckets().get(0)));
    }

    @Transactional
    @Test
    void testFindHostsWhenAccountIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account1", RHEL, ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory1");
    }

    @Transactional
    @Test
    void testFindHostsWhenProductIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", COOL_PROD, ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory2");
    }

    @Transactional
    @Test
    void testFindHostsWhenSLAIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", RHEL, ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory4");
    }

    @Transactional
    @Test
    void testFindHostsWhenUsageIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", RHEL, ServiceLevel.SELF_SUPPORT,
            Usage.DISASTER_RECOVERY, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10));
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
        Page<TallyHostView> hosts = repo.getTallyHostViews("account4", null, null, null, null,
            0, 0, PageRequest.of(0, 10));
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
        toSave.forEach(x->x.setDisplayName(DEFAULT_DISPLAY_NAME));
        repo.saveAll(toSave);
        repo.flush();

        Page<Host> guests = repo.getHostsByHypervisor(account, uuid, PageRequest.of(0, 10));
        assertEquals(1, guests.getTotalElements());
        assertEquals(uuid, guests.getContent().get(0).getHypervisorUuid());
        assertEquals("guest", guests.getContent().get(0).getInventoryId());
        assertEquals("INSIGHTS_guest", guests.getContent().get(0).getInsightsId());
    }

    @Transactional
    @Test
    void testCanSortByIdForImplicitSort() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10, Sort.Direction.ASC, "id"));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory3");
    }

    @Transactional
    @Test
    void testCanSortByDisplayName() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10, Sort.Direction.ASC,
            HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory3");
    }

    @Transactional
    @Test
    void testCanSortByMeasurementType() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account3", RHEL, ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10, Sort.Direction.ASC,
            HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.DISPLAY_NAME)));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory7");
    }

    @Transactional
    @Test
    void testCanSortByCores() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest
            .of(0, 10, Sort.Direction.ASC, HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.CORES)));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory3");
    }

    @Transactional
    @Test
    void testCanSortBySockets() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest
            .of(0, 10, Sort.Direction.ASC, HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.SOCKETS)));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory3");
    }

    @Transactional
    @Test
    void testCanSortByLastSeen() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10, Sort.Direction.ASC,
            HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.LAST_SEEN)));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory3");
    }

    @Transactional
    @Test
    void testCanSortByHardwareType() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10, Sort.Direction.ASC,
            HostsResource.SORT_PARAM_MAPPING.get(HostReportSort.HARDWARE_TYPE)));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory3");
    }

    @Transactional
    @Test
    void testDeleteByAccount() {
        Host host1 = createHost("h1", "A1");
        host1.setDisplayName(DEFAULT_DISPLAY_NAME);
        Host h1 = repo.saveAndFlush(host1);

        Host host2 = createHost("h2", "A2");
        host2.setDisplayName(DEFAULT_DISPLAY_NAME);
        Host h2 = repo.saveAndFlush(host2);

        Host host3 = createHost("h3", "A3");
        host3.setDisplayName(DEFAULT_DISPLAY_NAME);
        Host h3 = repo.saveAndFlush(host3);

        assertTrue(repo.findById(h1.getId()).isPresent());
        assertTrue(repo.findById(h2.getId()).isPresent());
        assertTrue(repo.findById(h3.getId()).isPresent());

        assertEquals(2, repo.deleteByAccountNumberIn(Arrays.asList("A1", "A2")));
    }

    @Transactional
    @Test
    void testGetHostViews() {
        Host host1 = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
        host1.setSockets(1);
        host1.setCores(1);
        host1.setNumOfGuests(4);

        host1.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 4, 2,
            HardwareMeasurementType.PHYSICAL);
        host1.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false, 10, 5,
            HardwareMeasurementType.VIRTUAL);
        host1.addBucket("Satellite", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 4, 2,
            HardwareMeasurementType.PHYSICAL);


        List<Host> toPersist = Arrays.asList(
            host1,
            new Host("INV2", "HOST2", "my_acct", "my_org", "sub2_id"),
            new Host("INV3", "HOST3", "my_acct2", "another_org", "sub3_id")
        );

        toPersist.forEach(x -> x.setDisplayName(DEFAULT_DISPLAY_NAME));
        repo.saveAll(toPersist);
        repo.flush();

        Page<TallyHostView> results = repo.getTallyHostViews("my_acct", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10));
        Map<String, TallyHostView> hosts = results.getContent().stream()
            .collect(Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
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

    @Transactional
    @Test
    void testNullNumGuests() {
        Host host = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
        host.setDisplayName(DEFAULT_DISPLAY_NAME);
        host.setSockets(1);
        host.setCores(1);

        host.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 4, 2,
            HardwareMeasurementType.PHYSICAL);

        List<Host> toPersist = Collections.singletonList(host);
        repo.saveAll(toPersist);
        repo.flush();

        Page<TallyHostView> results = repo.getTallyHostViews("my_acct", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 0, PageRequest.of(0, 10));
        Map<String, TallyHostView> hosts = results.getContent().stream()
            .collect(Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
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
        coreHost.setSockets(0);
        coreHost.setCores(1);
        coreHost.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 0, 1,
            HardwareMeasurementType.PHYSICAL);

        Host socketHost = new Host("INV2", "HOST2", "my_acct", "my_org", "sub_id");
        socketHost.setDisplayName(DEFAULT_DISPLAY_NAME);
        socketHost.setSockets(1);
        socketHost.setCores(0);
        socketHost.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 1, 0,
            HardwareMeasurementType.PHYSICAL);

        List<Host> toPersist = Arrays.asList(coreHost, socketHost);
        repo.saveAll(toPersist);
        repo.flush();

        Page<TallyHostView> results = repo.getTallyHostViews("my_acct", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 0, 1, PageRequest.of(0, 10));
        Map<String, TallyHostView> hosts = results.getContent().stream()
            .collect(Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
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
        coreHost.setSockets(0);
        coreHost.setCores(1);
        coreHost.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 0, 1,
            HardwareMeasurementType.PHYSICAL);

        Host socketHost = new Host("INV2", "HOST2", "my_acct", "my_org", "sub_id");
        socketHost.setDisplayName(DEFAULT_DISPLAY_NAME);
        socketHost.setSockets(1);
        socketHost.setCores(0);
        socketHost.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 1, 0,
            HardwareMeasurementType.PHYSICAL);

        List<Host> toPersist = Arrays.asList(coreHost, socketHost);
        repo.saveAll(toPersist);
        repo.flush();

        Page<TallyHostView> results = repo.getTallyHostViews("my_acct", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, SANITIZED_MISSING_DISPLAY_NAME, 1, 0, PageRequest.of(0, 10));
        Map<String, TallyHostView> hosts = results.getContent().stream()
            .collect(Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
        assertEquals(1, hosts.size());

        assertTrue(hosts.containsKey(HardwareMeasurementType.PHYSICAL.toString()));
        TallyHostView physical = hosts.get(HardwareMeasurementType.PHYSICAL.toString());
        assertEquals(coreHost.getInventoryId(), physical.getInventoryId());
        assertEquals(coreHost.getSubscriptionManagerId(), physical.getSubscriptionManagerId());
        assertEquals(0, physical.getSockets());
        assertEquals(1, physical.getCores());
    }

    @Transactional
    @ParameterizedTest
    @CsvSource({
        "'',3",
        "banana,1",
        "rang,1",
        "an,2",
        "celery,0"
    })
    void testDisplayNameFilter(String displayNameSubstring,
        Integer expectedResults) {

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
        repo.saveAll(toPersist);
        repo.flush();

        int sockets = 4;
        int cores = 2;

        Page<TallyHostView> results = repo.getTallyHostViews(acctNumber, RHEL, ServiceLevel.PREMIUM,
            Usage.PRODUCTION, displayNameSubstring, cores, sockets, null);

        int expected = expectedResults;
        int actual = results.getContent().size();

        assertEquals(expected, actual);

    }

    private Host createHost(String inventoryId, String account) {
        Host host = new Host(inventoryId, "INSIGHTS_" + inventoryId, account, "ORG_" + account,
            "SUBMAN_" + inventoryId);
        host.setSockets(1);
        host.setCores(1);
        return host;
    }

    private HostTallyBucket addBucketToHost(Host host, String productId, ServiceLevel sla, Usage usage) {
        return addBucketToHost(host, productId, sla, usage, HardwareMeasurementType.PHYSICAL);
    }

    private HostTallyBucket addBucketToHost(Host host, String productId, ServiceLevel sla, Usage usage,
        HardwareMeasurementType measurementType) {
        return host.addBucket(productId, sla, usage, true, 4, 2, measurementType);
    }

    private void assertTallyHostView(TallyHostView host, String inventoryId) {
        assertNotNull(host);
        assertEquals(inventoryId, host.getInventoryId());
        assertEquals("INSIGHTS_" + inventoryId, host.getInsightsId());
        assertEquals("SUBMAN_" + inventoryId, host.getSubscriptionManagerId());
    }
}
