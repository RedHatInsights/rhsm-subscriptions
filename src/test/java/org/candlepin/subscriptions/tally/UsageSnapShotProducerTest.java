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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.files.AccountListSource;
import org.candlepin.subscriptions.files.RhelProductListSource;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHost;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.normalizer.RhsmFactNormalizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UsageSnapShotProducerTest {

    private AccountListSource accountListSourceMock;
    private RhelProductListSource productListSourceMock;
    private InventoryRepository inventoryRepo;
    private TallySnapshotRepository tallyRepoMock;
    private FactNormalizer factNormalizer;
    private UsageSnapshotProducer producer;
    private Clock clock = Clock.systemUTC();


    @BeforeEach
    public void setupTest() throws Exception {
        inventoryRepo = mock(InventoryRepository.class);
        tallyRepoMock = mock(TallySnapshotRepository.class);
        accountListSourceMock = mock(AccountListSource.class);
        productListSourceMock = mock(RhelProductListSource.class);

        List<String> rhelProducts = Arrays.asList("RHEL");
        when(productListSourceMock.list()).thenReturn(rhelProducts);

        ApplicationProperties appProps = new ApplicationProperties();
        appProps.setAccountBatchSize(500);

        factNormalizer = new FactNormalizer(new ApplicationProperties(), productListSourceMock, clock);
        producer = new UsageSnapshotProducer(factNormalizer, accountListSourceMock, inventoryRepo,
            tallyRepoMock, clock, appProps);
    }

    @Test
    public void testProduceEmptySnapshotWhenNoInventoryFoundForAccount() throws Exception {
        // When no inventory data is found, a snapshot is still created for the configured account,
        // but the counts should all be zero.
        when(accountListSourceMock.list()).thenReturn(Arrays.asList("A1"));
        ArgumentCaptor<List<TallySnapshot>> saveArgCapture = ArgumentCaptor.forClass(List.class);
        producer.produceSnapshots();

        verify(tallyRepoMock).saveAll(saveArgCapture.capture());

        List<TallySnapshot> saved = saveArgCapture.getValue();
        assertEquals(1, saved.size());
        TallySnapshot emptySnapshot = saved.get(0);
        assertEquals("A1", emptySnapshot.getAccountNumber());
        assertEquals("RHEL", emptySnapshot.getProductId());
        assertEquals(Integer.valueOf(0), emptySnapshot.getCores());
        assertEquals(Integer.valueOf(0), emptySnapshot.getInstanceCount());
        // Would not have been able to determine the owner ID until at least
        // one host was reported.
        assertNull(emptySnapshot.getOwnerId());
        assertEquals(TallyGranularity.DAILY, emptySnapshot.getGranularity());
    }

    @Test
    public void testTallyCoresOfRhelWhenInventoryFoundForAccount() throws Exception {
        when(accountListSourceMock.list()).thenReturn(Arrays.asList("A1", "A2"));
        ArgumentCaptor<List<TallySnapshot>> saveArgCapture = ArgumentCaptor.forClass(List.class);

        InventoryHost host1 = createHost("A1", "O1", "RHEL", 4);
        InventoryHost host2 = createHost("A1", "O1", "RHEL", 8);
        InventoryHost host3 = createHost("A2", "O2", "RHEL", 2);
        when(inventoryRepo.findByAccount(eq("A1"))).thenReturn(Arrays.asList(host1, host2).stream());
        when(inventoryRepo.findByAccount(eq("A2"))).thenReturn(Arrays.asList(host3).stream());

        producer.produceSnapshots();

        verify(tallyRepoMock).saveAll(saveArgCapture.capture());

        List<TallySnapshot> saved = saveArgCapture.getValue();
        assertEquals(2, saved.size());

        boolean a1Checked = false;
        boolean a2Checked = false;
        for (TallySnapshot snap : saved) {
            String account = snap.getAccountNumber();
            if ("A1".equals(account)) {
                assertEquals("A1", snap.getAccountNumber());
                assertEquals("RHEL", snap.getProductId());
                assertEquals(Integer.valueOf(12), snap.getCores());
                assertEquals(Integer.valueOf(2), snap.getInstanceCount());
                assertEquals("O1", snap.getOwnerId());
                assertEquals(TallyGranularity.DAILY, snap.getGranularity());
                a1Checked = true;
            }
            else if ("A2".equals(account)) {
                assertEquals("A2", snap.getAccountNumber());
                assertEquals("RHEL", snap.getProductId());
                assertEquals(Integer.valueOf(2), snap.getCores());
                assertEquals(Integer.valueOf(1), snap.getInstanceCount());
                assertEquals("O2", snap.getOwnerId());
                assertEquals(TallyGranularity.DAILY, snap.getGranularity());
                a2Checked = true;
            }
            else {
                fail("Unexpected account!");
            }
        }
        assertTrue(a1Checked && a2Checked);
    }

    @Test
    public void testUpdatesTallySnapshotIfOneAlreadyExists() throws Exception {
        when(accountListSourceMock.list()).thenReturn(Arrays.asList("A1"));
        ArgumentCaptor<List<TallySnapshot>> saveArgCapture = ArgumentCaptor.forClass(List.class);

        InventoryHost host = createHost("A1", "O1", "RHEL", 4);
        when(inventoryRepo.findByAccount(eq("A1"))).thenReturn(Arrays.asList(host).stream());

        TallySnapshot existing = new TallySnapshot();
        existing.setId(UUID.randomUUID());
        existing.setAccountNumber("A1");

        when(tallyRepoMock.findByAccountNumberInAndProductIdAndGranularityAndSnapshotDateBetween(
            anyCollection(), eq("RHEL"), eq(TallyGranularity.DAILY), any(OffsetDateTime.class),
            any(OffsetDateTime.class))
        ).thenReturn(Arrays.asList(existing));

        producer.produceSnapshots();

        verify(tallyRepoMock).saveAll(saveArgCapture.capture());

        List<TallySnapshot> saved = saveArgCapture.getValue();
        assertEquals(1, saved.size());

        TallySnapshot snap = saved.get(0);
        assertEquals(existing.getId(), snap.getId());
        assertEquals("A1", snap.getAccountNumber());
        assertEquals("RHEL", snap.getProductId());
        assertEquals(Integer.valueOf(4), snap.getCores());
        assertEquals(Integer.valueOf(1), snap.getInstanceCount());
        assertEquals("O1", snap.getOwnerId());
        assertEquals(TallyGranularity.DAILY, snap.getGranularity());
    }

    @Test
    public void testSnapshotDoesNotIncludeHostWhenProductDoesntMatch() throws IOException {
        InventoryHost h1 = createHost("A1", "Owner1", "RHEL", 8);
        InventoryHost h2 = createHost("A1", "Owner1", "NOT_RHEL", 12);
        when(inventoryRepo.findByAccount(eq("A1"))).thenReturn(Arrays.asList(h1, h2).stream());

        when(accountListSourceMock.list()).thenReturn(Arrays.asList("A1"));
        ArgumentCaptor<List<TallySnapshot>> saveArgCapture = ArgumentCaptor.forClass(List.class);
        producer.produceSnapshots();

        verify(tallyRepoMock).saveAll(saveArgCapture.capture());

        List<TallySnapshot> saved = saveArgCapture.getValue();
        assertEquals(1, saved.size());
        TallySnapshot emptySnapshot = saved.get(0);
        assertEquals("A1", emptySnapshot.getAccountNumber());
        assertEquals("RHEL", emptySnapshot.getProductId());
        assertEquals(Integer.valueOf(8), emptySnapshot.getCores());
        assertEquals(Integer.valueOf(1), emptySnapshot.getInstanceCount());
        assertEquals("Owner1", emptySnapshot.getOwnerId());
        assertEquals(TallyGranularity.DAILY, emptySnapshot.getGranularity());
    }

    @Test
    public void throwsISEOnAttemptToCalculateFactsBelongingToADifferentOwner() throws IOException {
        InventoryHost h1 = createHost("A1", "Owner1", "RHEL", 1);
        InventoryHost h2 = createHost("A1", "Owner2", "RHEL", 1);
        when(inventoryRepo.findByAccount(eq("A1"))).thenReturn(Arrays.asList(h1, h2).stream());
        when(accountListSourceMock.list()).thenReturn(Arrays.asList("A1"));

        Throwable e = assertThrows(IllegalStateException.class, () -> producer.produceSnapshots());

        String expectedMessage = String.format("Attempt to set a different owner for an account: %s:%s",
            "Owner1", "Owner2");
        assertEquals(expectedMessage, e.getMessage());

    }

    private InventoryHost createHost(String account, String orgId, String product, Integer cores) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.CPU_CORES, cores);
        rhsmFacts.put(RhsmFactNormalizer.RH_PRODUCTS, Arrays.asList(product));
        rhsmFacts.put(RhsmFactNormalizer.ORG_ID, orgId);

        Map<String, Map<String, Object>> facts = new HashMap<>();
        facts.put(FactSetNamespace.RHSM, rhsmFacts);

        InventoryHost host = new InventoryHost();
        host.setAccount(account);
        host.setFacts(facts);
        return host;
    }
}
