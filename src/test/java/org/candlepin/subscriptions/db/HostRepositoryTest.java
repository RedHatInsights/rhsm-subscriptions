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

import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
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

    @Autowired
    private HostRepository repo;

    @Transactional
    @BeforeAll
    void setupTestData() {

        // ACCOUNT 1 HOSTS
        Host host1 = createHost("insights1", "account1", "org1");
        addBucketToHost(host1, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);
        addBucketToHost(host1, "Satellite", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);

        Host host2 = createHost("insights2", "account1", "org1");
        addBucketToHost(host2, "RHEL", ServiceLevel.SELF_SUPPORT, Usage.DEVELOPMENT_TEST, false);
        addBucketToHost(host2, "Satellite", ServiceLevel.SELF_SUPPORT, Usage.DEVELOPMENT_TEST, false);

        // ACCOUNT 2 HOSTS
        Host host3 = createHost("insights3", "account2", "org2");
        addBucketToHost(host3, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true);
        addBucketToHost(host3, "Satellite", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);

        Host host4 = createHost("insights4", "account2", "org2");
        addBucketToHost(host4, "RHEL", ServiceLevel.SELF_SUPPORT, Usage.DEVELOPMENT_TEST, false);
        addBucketToHost(host4, "SUPER_COOL_PRODUCT", ServiceLevel.ANY, Usage.ANY, true);

        Host host5 = createHost("insights5", "account2", "org2");
        addBucketToHost(host5, "Satellite", ServiceLevel.SELF_SUPPORT, Usage.DEVELOPMENT_TEST, false);

        // ACCOUNT 3 HOSTS
        Host host6 = createHost("insights6", "account3", "org3");
        addBucketToHost(host6, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);

        Host host7 = createHost("insights7", "account3", "org3");
        addBucketToHost(host7, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true);

        // ACCOUNT 4 HOSTS
        Host host8 = createHost("insights8", "account4", "org4");

        // ACCOUNT 5 HOSTS
        Host hypervisor = createHost("hypervisor", "account5", "org5");
        addBucketToHost(hypervisor, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true);
        Host guest = createHost("guest", "account5", "org5");
        guest.setGuest(true);
        addBucketToHost(guest, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);

        List<Host> toSave = Arrays.asList(host1, host2, host3, host4, host5, host6, host7, host8,
            hypervisor, guest);
        repo.saveAll(toSave);
        repo.flush();
    }

    @Transactional
    @Test
    void testCreate() {
        Host host = new Host("HOST1", "my_acct", "my_org");
        host.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getInsightsId());
        assertTrue(result.isPresent());
        Host saved = result.get();
        assertEquals(1, saved.getBuckets().size());
    }

    @Transactional
    @Test
    void testUpdate() {
        Host host = new Host("HOST1", "my_acct", "my_org");
        host.setSockets(1);
        host.setCores(1);
        host.addBucket("RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);
        host.addBucket("Satellite", ServiceLevel.PREMIUM, Usage.PRODUCTION, true);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getInsightsId());
        assertTrue(result.isPresent());
        Host toUpdate = result.get();
        assertEquals(2, toUpdate.getBuckets().size());

        toUpdate.setAccountNumber("updated_acct_num");
        toUpdate.setSockets(4);
        toUpdate.setCores(8);
        toUpdate.removeBucket(host.getBuckets().get(0));

        repo.saveAndFlush(toUpdate);

        Optional<Host> updateResult = repo.findById(toUpdate.getInsightsId());
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
    void findHostsByBucketCriteria() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account2", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, true, null, PageRequest.of(0, 10));
        List<Host> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertHost(found.get(0), "insights3", "account2", "org2");
    }

    @Transactional
    @Test
    void findHostsByAnyBucketProduct() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account2", null, ServiceLevel.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST, false, null, PageRequest.of(0, 10));
        Map<String, Host> found = hosts.stream().collect(
            Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(2, found.size());
        assertHost(found.get("insights4"), "insights4", "account2", "org2");
        assertHost(found.get("insights5"), "insights5", "account2", "org2");
    }

    @Transactional
    @Test
    void findHostsByAnyBucketSlaAndUsage() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account1", "RHEL", null, null, false,
            null, PageRequest.of(0, 10));
        Map<String, Host> found = hosts.stream().collect(
            Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(2, found.size());
        assertHost(found.get("insights1"), "insights1", "account1", "org1");
        assertHost(found.get("insights2"), "insights2", "account1", "org1");
    }

    @Transactional
    @Test
    void findHostsByAnyBucketAsHypervisor() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account3", "RHEL", ServiceLevel.PREMIUM,
            Usage.PRODUCTION, null, null, PageRequest.of(0, 10));
        Map<String, Host> found = hosts.stream().collect(
            Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(2, found.size());
        assertHost(found.get("insights6"), "insights6", "account3", "org3");
        assertHost(found.get("insights7"), "insights7", "account3", "org3");
    }

    @Transactional
    @Test
    void findHostsWithoutBucketCriterial() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account2", null, null, null, null,
            null, PageRequest.of(0, 10));
        Map<String, Host> found =
            hosts.stream().collect(Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(3, found.size());
        assertHost(found.get("insights3"), "insights3", "account2", "org2");
        assertHost(found.get("insights4"), "insights4", "account2", "org2");
        assertHost(found.get("insights5"), "insights5", "account2", "org2");
    }

    @Transactional
    @Test
    void testNoHostFoundWhenItHasNoBucket() {
        Optional<Host> existing = repo.findById("insights8");
        assertTrue(existing.isPresent());
        assertEquals("account4", existing.get().getAccountNumber());

        // When a host has no buckets, it will not be returned.
        Page<Host> hosts = repo.getHostsByBucketCriteria("account4", null, null, null, null,
            null, PageRequest.of(0, 10));
        assertEquals(0, hosts.stream().count());
    }

    @Transactional
    @Test
    void testHypervisorFoundWhenGuestFalse() {
        Page<Host> existing = repo.getHostsByBucketCriteria("account5", "RHEL", null, null, null,
            false, PageRequest.of(0, 10));
        assertEquals(1, existing.getTotalElements());
        assertEquals("hypervisor", existing.getContent().get(0).getInsightsId());
    }

    @Transactional
    @Test
    void testHypervisorFoundWhenGuestTrue() {
        Page<Host> existing = repo.getHostsByBucketCriteria("account5", "RHEL", null, null, null,
            true, PageRequest.of(0, 10));
        assertEquals(1, existing.getTotalElements());
        assertEquals("guest", existing.getContent().get(0).getInsightsId());
    }

    @Transactional
    @Test
    void testHypervisorAndGuestFoundWhenGuestNull() {
        Page<Host> existing = repo.getHostsByBucketCriteria("account5", "RHEL", null, null, null,
            null, PageRequest.of(0, 10));
        assertEquals(2, existing.getTotalElements());
    }

    @Transactional
    @Test
    void testReturnsGuestsOfHypervisor() {
        String account = "hostGuestTest";
        String uuid = UUID.randomUUID().toString();

        Host hypervisor = createHost("hypervisor", account, "org");
        hypervisor.setSubscriptionManagerId(uuid);
        addBucketToHost(hypervisor, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, true);

        Host guest = createHost("guest", account, "org");
        guest.setGuest(true);
        guest.setHypervisorUuid(uuid);
        addBucketToHost(guest, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);

        Host unmappedGuest = createHost("unmappedGuest", account, "org");
        unmappedGuest.setGuest(true);
        addBucketToHost(unmappedGuest, "RHEL", ServiceLevel.PREMIUM, Usage.PRODUCTION, false);

        List<Host> toSave = Arrays.asList(hypervisor, guest, unmappedGuest);
        repo.saveAll(toSave);
        repo.flush();

        Page<Host> guests = repo.getHostsByHypervisor(account, uuid, PageRequest.of(0, 10));
        assertEquals(1, guests.getTotalElements());
        assertEquals(uuid, guests.getContent().get(0).getHypervisorUuid());
        assertEquals("guest", guests.getContent().get(0).getInsightsId());
    }

    private Host createHost(String insightsId, String account, String orgId) {
        Host host = new Host(insightsId, account, orgId);
        host.setSockets(1);
        host.setCores(1);
        return host;
    }

    private HostTallyBucket addBucketToHost(Host host, String productId, ServiceLevel sla, Usage usage,
        Boolean asHypervisor) {
        return host.addBucket(productId, sla, usage, asHypervisor);
    }

    private void assertHost(Host host, String insightsId, String accountNumber, String orgId) {
        assertNotNull(host);
        assertEquals(insightsId, host.getInsightsId());
        assertEquals(accountNumber, host.getAccountNumber());
        assertEquals(orgId, host.getOrgId());
    }
}
