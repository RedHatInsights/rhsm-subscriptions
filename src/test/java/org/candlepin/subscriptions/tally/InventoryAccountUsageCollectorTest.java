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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.RhelProductListSource;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHost;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.FactSetNamespace;
import org.candlepin.subscriptions.tally.facts.normalizer.RhsmFactNormalizer;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InventoryAccountUsageCollectorTest {

    private static final String TEST_PRODUCT = "RHEL";

    private ApplicationClock clock;
    private RhelProductListSource productListSource;
    private Collection<String> rhelProducts;
    private InventoryRepository inventoryRepo;
    private FactNormalizer factNormalizer;
    private InventoryAccountUsageCollector collector;

    @BeforeEach
    public void setupTest() throws Exception {
        productListSource = mock(RhelProductListSource.class);

        rhelProducts = Arrays.asList(TEST_PRODUCT);
        when(productListSource.list()).thenReturn(new ArrayList<>(rhelProducts));

        clock = new ApplicationClock();
        inventoryRepo = mock(InventoryRepository.class);
        factNormalizer = new FactNormalizer(new ApplicationProperties(), productListSource, clock);
        collector = new InventoryAccountUsageCollector(factNormalizer, inventoryRepo);
    }

    @Test
    public void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() throws Exception {
        Collection<String> targetAccounts = Arrays.asList("A1", "A2");
        InventoryHost host1 = createHost("A1", "O1", TEST_PRODUCT, 4, 4);
        InventoryHost host2 = createHost("A1", "O1", TEST_PRODUCT, 8, 4);
        InventoryHost host3 = createHost("A2", "O2", TEST_PRODUCT, 2, 6);
        when(inventoryRepo.findByAccountIn(eq(targetAccounts)))
            .thenReturn(Arrays.asList(host1, host2, host3).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts)
            .stream()
            .collect(Collectors.toMap(AccountUsageCalculation::getAccount, Function.identity()));
        assertEquals(2, calcs.size());
        assertTrue(calcs.containsKey("A1"));
        assertTrue(calcs.containsKey("A2"));

        AccountUsageCalculation a1Calc = calcs.get("A1");
        assertEquals(1, a1Calc.getProducts().size());
        assertCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 12, 8, 2);

        AccountUsageCalculation a2Calc = calcs.get("A2");
        assertEquals(1, a2Calc.getProducts().size());
        assertCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 2, 6, 1);
    }

    @Test
    public void testCalculationDoesNotIncludeHostWhenProductDoesntMatch() throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");
        InventoryHost h1 = createHost("A1", "Owner1", TEST_PRODUCT, 8, 12);
        InventoryHost h2 = createHost("A1", "Owner1", "NOT_RHEL", 12, 14);
        when(inventoryRepo.findByAccountIn(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts)
            .stream()
            .collect(Collectors.toMap(AccountUsageCalculation::getAccount, Function.identity()));
        assertEquals(1, calcs.size());
        assertTrue(calcs.containsKey("A1"));

        AccountUsageCalculation accountCalc = calcs.get("A1");
        assertEquals(1, accountCalc.getProducts().size());
        assertCalculation(accountCalc, "A1", "Owner1", TEST_PRODUCT, 8, 12, 1);
    }

    @Test
    public void throwsISEOnAttemptToCalculateFactsBelongingToADifferentOwnerForSameAccount()
        throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");

        InventoryHost h1 = createHost("A1", "Owner1", TEST_PRODUCT, 1, 2);
        InventoryHost h2 = createHost("A1", "Owner2", TEST_PRODUCT, 1, 2);
        when(inventoryRepo.findByAccountIn(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Throwable e = assertThrows(IllegalStateException.class,
            () -> collector.collect(rhelProducts, targetAccounts));

        String expectedMessage = String.format("Attempt to set a different owner for an account: %s:%s",
            "Owner1", "Owner2");
        assertEquals(expectedMessage, e.getMessage());
    }

    private InventoryHost createHost(String account, String orgId, String product, int cores, int sockets) {
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

    private void assertCalculation(AccountUsageCalculation calc, String account, String owner, String product,
        int cores, int sockets, int instances) {
        assertEquals(account, calc.getAccount());
        assertEquals(owner, calc.getOwner());
        assertTrue(calc.containsProductCalculation(product));

        ProductUsageCalculation prodCalc = calc.getProductCalculation(product);
        assertEquals(product, prodCalc.getProductId());
        assertEquals(cores, prodCalc.getTotalCores());
        assertEquals(sockets, prodCalc.getTotalSockets());
        assertEquals(instances, prodCalc.getInstanceCount());
    }
}
