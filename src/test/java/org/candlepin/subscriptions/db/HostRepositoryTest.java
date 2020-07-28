/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyHostView;
import org.candlepin.subscriptions.db.model.Usage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@TestPropertySource("classpath:/test.properties")
class HostRepositoryTest {

    private final String RHEL = "RHEL";
    private final String COOL_PROD = "COOL_PROD";

    @Autowired
    private HostRepository repo;

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

        List<Host> toSave = Arrays.asList(host1, host2, host3, host4, host5, host6);
        repo.saveAll(toSave);
        repo.flush();
    }

    @Transactional
    @Test
    void testCreate() {
        Host host = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
        host.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false, 4, 2,
            HardwareMeasurementType.PHYSICAL);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getInventoryId());
        assertTrue(result.isPresent());
        Host saved = result.get();
        assertEquals(1, saved.getBuckets().size());
    }

    @Transactional
    @Test
    void testUpdate() {
        Host host = new Host("INV1", "HOST1", "my_acct", "my_org", "sub_id");
        host.setSockets(1);
        host.setCores(1);

        host.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false, 4, 2,
            HardwareMeasurementType.PHYSICAL);
        host.addBucket("Satellite", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 4, 2,
            HardwareMeasurementType.PHYSICAL);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getInventoryId());
        assertTrue(result.isPresent());
        Host toUpdate = result.get();
        assertEquals(2, toUpdate.getBuckets().size());

        toUpdate.setAccountNumber("updated_acct_num");
        toUpdate.setSockets(4);
        toUpdate.setCores(8);
        toUpdate.removeBucket(host.getBuckets().get(0));

        repo.saveAndFlush(toUpdate);

        Optional<Host> updateResult = repo.findById(toUpdate.getInventoryId());
        assertTrue(updateResult.isPresent());
        Host updated = updateResult.get();
        assertEquals("updated_acct_num", updated.getAccountNumber());
        assertEquals(4, updated.getSockets().intValue());
        assertEquals(8, updated.getCores().intValue());
        assertEquals(1, updated.getBuckets().size());
        assertTrue(updated.getBuckets().contains(host.getBuckets().get(1)));
    }

    @Transactional
    @Test
    void testFindHostsWhenAccountIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account1", RHEL, ServiceLevel.PREMIUM,
            Usage.PRODUCTION, PageRequest.of(0, 10));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory1");
    }

    @Transactional
    @Test
    void testFindHostsWhenProductIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", COOL_PROD, ServiceLevel.PREMIUM,
            Usage.PRODUCTION, PageRequest.of(0, 10));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory2");
    }

    @Transactional
    @Test
    void testFindHostsWhenSLAIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", RHEL, ServiceLevel.SELF_SUPPORT,
            Usage.PRODUCTION, PageRequest.of(0, 10));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory4");
    }

    @Transactional
    @Test
    void testFindHostsWhenUsageIsDifferent() {
        Page<TallyHostView> hosts = repo.getTallyHostViews("account2", RHEL, ServiceLevel.SELF_SUPPORT,
            Usage.DISASTER_RECOVERY, PageRequest.of(0, 10));
        List<TallyHostView> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertTallyHostView(found.get(0), "inventory5");
    }

    @Transactional
    @Test
    void testNoHostFoundWhenItHasNoBucket() {
        Optional<Host> existing = repo.findById("inventory6");
        assertTrue(existing.isPresent());
        assertEquals("account3", existing.get().getAccountNumber());

        // When a host has no buckets, it will not be returned.
        Page<TallyHostView> hosts = repo.getTallyHostViews("account4", null, null, null,
            PageRequest.of(0, 10));
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
    void testDeleteByAccount() {
        Host h1 = createHost("h1", "A1");
        Host h2 = createHost("h2", "A2");

        repo.saveAll(Arrays.asList(h1, h2));
        repo.flush();

        assertTrue(repo.findById(h1.getInventoryId()).isPresent());
        assertTrue(repo.findById(h2.getInventoryId()).isPresent());

        repo.deleteByAccountNumberIn(Arrays.asList("A1"));
        repo.flush();

        assertFalse(repo.findById(h1.getInventoryId()).isPresent());
        assertTrue(repo.findById(h2.getInventoryId()).isPresent());
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
            HardwareMeasurementType.HYPERVISOR);
        host1.addBucket("Satellite", ServiceLevel.PREMIUM, Usage.PRODUCTION, true, 4, 2,
            HardwareMeasurementType.PHYSICAL);


        List<Host> toPersist = Arrays.asList(
            host1,
            new Host("INV2", "HOST2", "my_acct", "my_org", "sub2_id"),
            new Host("INV3", "HOST3", "my_acct2", "another_org", "sub3_id")
        );
        repo.saveAll(toPersist);
        repo.flush();

        Page<TallyHostView> results = repo.getTallyHostViews("my_acct", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, PageRequest.of(0, 10));
        Map<String, TallyHostView> hosts = results.getContent().stream()
            .collect(Collectors.toMap(TallyHostView::getHardwareMeasurementType, Function.identity()));
        assertEquals(2, hosts.size());

        assertTrue(hosts.containsKey(HardwareMeasurementType.PHYSICAL.toString()));
        TallyHostView physical = hosts.get(HardwareMeasurementType.PHYSICAL.toString());
        assertEquals(host1.getInventoryId(), physical.getInventoryId());
        assertEquals(host1.getSubscriptionManagerId(), physical.getSubscriptionManagerId());
        assertEquals(host1.getNumOfGuests().intValue(), physical.getNumberOfGuests());
        assertEquals(4, physical.getSockets());
        assertEquals(2, physical.getCores());

        assertTrue(hosts.containsKey(HardwareMeasurementType.HYPERVISOR.toString()));
        TallyHostView hypervisor = hosts.get(HardwareMeasurementType.HYPERVISOR.toString());
        assertEquals(host1.getInventoryId(), hypervisor.getInventoryId());
        assertEquals(host1.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
        assertEquals(host1.getNumOfGuests().intValue(), hypervisor.getNumberOfGuests());
        assertEquals(10, hypervisor.getSockets());
        assertEquals(5, hypervisor.getCores());
    }

    private Host createHost(String inventoryId, String account) {
        Host host = new Host(inventoryId, "INSIGHTS_" + inventoryId, account, "ORG_" + account,
            "SUBMAN_" + inventoryId);
        host.setSockets(1);
        host.setCores(1);
        return host;
    }

    private HostTallyBucket addBucketToHost(Host host, String productId, ServiceLevel sla, Usage usage) {
        return host.addBucket(productId, sla, usage, true, 4, 2, HardwareMeasurementType.PHYSICAL);
    }

    private void assertTallyHostView(TallyHostView host, String inventoryId) {
        assertNotNull(host);
        assertEquals(inventoryId, host.getInventoryId());
        assertEquals("INSIGHTS_" + inventoryId, host.getInsightsId());
        assertEquals("SUBMAN_" + inventoryId, host.getSubscriptionManagerId());
    }
}
