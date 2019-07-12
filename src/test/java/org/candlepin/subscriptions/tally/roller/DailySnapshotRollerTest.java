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
package org.candlepin.subscriptions.tally.roller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.candlepin.subscriptions.files.RhelProductListSource;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHost;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.normalizer.RhsmFactNormalizer;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DailySnapshotRollerTest {

    // Product name must be RHEL, otherwise it will not be included in the
    // normalized facts.
    private static final String TEST_PRODUCT = "RHEL";

    private RhelProductListSource productListSourceMock;
    private InventoryRepository inventoryRepo;
    private TallySnapshotRepository tallyRepoMock;
    private FactNormalizer factNormalizer;
    private DailySnapshotRoller roller;
    private ApplicationClock clock;

    @BeforeEach
    public void setupTest() throws Exception {
        clock = new ApplicationClock();
        inventoryRepo = mock(InventoryRepository.class);
        tallyRepoMock = mock(TallySnapshotRepository.class);
        productListSourceMock = mock(RhelProductListSource.class);

        List<String> rhelProducts = Arrays.asList(TEST_PRODUCT);
        when(productListSourceMock.list()).thenReturn(rhelProducts);

        ApplicationProperties appProps = new ApplicationProperties();
        appProps.setAccountBatchSize(500);

        factNormalizer = new FactNormalizer(new ApplicationProperties(), productListSourceMock, clock);
        roller = new DailySnapshotRoller(TEST_PRODUCT, inventoryRepo, tallyRepoMock, factNormalizer,
            clock);
    }

    @Test
    public void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() throws Exception {
        ArgumentCaptor<List<TallySnapshot>> saveArgCapture = ArgumentCaptor.forClass(List.class);

        List<String> targetAccounts = Arrays.asList("A1", "A2");
        InventoryHost host1 = createHost("A1", "O1", TEST_PRODUCT, 4, 4);
        InventoryHost host2 = createHost("A1", "O1", TEST_PRODUCT, 8, 4);
        InventoryHost host3 = createHost("A2", "O2", TEST_PRODUCT, 2, 6);
        when(inventoryRepo.findByAccountIn(eq(targetAccounts)))
            .thenReturn(Arrays.asList(host1, host2, host3).stream());

        roller.rollSnapshots(targetAccounts);

        verify(tallyRepoMock).saveAll(saveArgCapture.capture());

        List<TallySnapshot> saved = saveArgCapture.getValue();
        assertEquals(2, saved.size());

        boolean a1Checked = false;
        boolean a2Checked = false;
        for (TallySnapshot snap : saved) {
            String account = snap.getAccountNumber();
            if ("A1".equals(account)) {
                assertEquals("A1", snap.getAccountNumber());
                assertEquals(TEST_PRODUCT, snap.getProductId());
                assertEquals(Integer.valueOf(12), snap.getCores());
                assertEquals(Integer.valueOf(8), snap.getSockets());
                assertEquals(Integer.valueOf(2), snap.getInstanceCount());
                assertEquals("O1", snap.getOwnerId());
                assertEquals(TallyGranularity.DAILY, snap.getGranularity());
                a1Checked = true;
            }
            else if ("A2".equals(account)) {
                assertEquals("A2", snap.getAccountNumber());
                assertEquals(TEST_PRODUCT, snap.getProductId());
                assertEquals(Integer.valueOf(2), snap.getCores());
                assertEquals(Integer.valueOf(6), snap.getSockets());
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
        ArgumentCaptor<List<TallySnapshot>> saveArgCapture = ArgumentCaptor.forClass(List.class);
        List<String> targetAccounts = Arrays.asList("A1");
        InventoryHost host = createHost("A1", "O1", TEST_PRODUCT, 4, 8);
        when(inventoryRepo.findByAccountIn(eq(targetAccounts))).thenReturn(Arrays.asList(host).stream());

        TallySnapshot existing = new TallySnapshot();
        existing.setId(UUID.randomUUID());
        existing.setAccountNumber("A1");
        existing.setGranularity(TallyGranularity.DAILY);
        existing.setProductId(TEST_PRODUCT);

        when(tallyRepoMock.findByAccountNumberInAndProductIdAndGranularityAndSnapshotDateBetween(
            anyCollection(), eq(TEST_PRODUCT), eq(TallyGranularity.DAILY), any(OffsetDateTime.class),
            any(OffsetDateTime.class))
        ).thenReturn(Arrays.asList(existing).stream());

        roller.rollSnapshots(targetAccounts);

        verify(tallyRepoMock).saveAll(saveArgCapture.capture());

        List<TallySnapshot> saved = saveArgCapture.getValue();

        assertEquals(1, saved.size());

        TallySnapshot snap = saved.get(0);
        assertEquals(existing.getId(), snap.getId());
        assertEquals("A1", snap.getAccountNumber());
        assertEquals(TEST_PRODUCT, snap.getProductId());
        assertEquals(Integer.valueOf(4), snap.getCores());
        assertEquals(Integer.valueOf(8), snap.getSockets());
        assertEquals(Integer.valueOf(1), snap.getInstanceCount());
        assertEquals("O1", snap.getOwnerId());
        assertEquals(TallyGranularity.DAILY, snap.getGranularity());
    }

    @Test
    public void testSnapshotDoesNotIncludeHostWhenProductDoesntMatch() throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");

        InventoryHost h1 = createHost("A1", "Owner1", TEST_PRODUCT, 8, 12);
        InventoryHost h2 = createHost("A1", "Owner1", "NOT_RHEL", 12, 14);
        when(inventoryRepo.findByAccountIn(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        ArgumentCaptor<List<TallySnapshot>> saveArgCapture = ArgumentCaptor.forClass(List.class);
        roller.rollSnapshots(targetAccounts);

        verify(tallyRepoMock).saveAll(saveArgCapture.capture());
        List<TallySnapshot> saved = saveArgCapture.getValue();

        assertEquals(1, saved.size());
        TallySnapshot emptySnapshot = saved.get(0);
        assertEquals("A1", emptySnapshot.getAccountNumber());
        assertEquals(TEST_PRODUCT, emptySnapshot.getProductId());
        assertEquals(Integer.valueOf(8), emptySnapshot.getCores());
        assertEquals(Integer.valueOf(12), emptySnapshot.getSockets());
        assertEquals(Integer.valueOf(1), emptySnapshot.getInstanceCount());
        assertEquals("Owner1", emptySnapshot.getOwnerId());
        assertEquals(TallyGranularity.DAILY, emptySnapshot.getGranularity());
    }

    @Test
    public void throwsISEOnAttemptToCalculateFactsBelongingToADifferentOwner() throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");

        InventoryHost h1 = createHost("A1", "Owner1", TEST_PRODUCT, 1, 2);
        InventoryHost h2 = createHost("A1", "Owner2", TEST_PRODUCT, 1, 2);
        when(inventoryRepo.findByAccountIn(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Throwable e = assertThrows(IllegalStateException.class,
            () -> roller.rollSnapshots(Arrays.asList("A1")));

        String expectedMessage = String.format("Attempt to set a different owner for an account: %s:%s",
            "Owner1", "Owner2");
        assertEquals(expectedMessage, e.getMessage());
    }

    private InventoryHost createHost(String account, String orgId, String product, Integer cores,
        Integer sockets) {
        Map<String, Object> rhsmFacts = new HashMap<>();
        rhsmFacts.put(RhsmFactNormalizer.CPU_CORES, cores);
        rhsmFacts.put(RhsmFactNormalizer.CPU_SOCKETS, sockets);
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
