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

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InventoryAccountUsageCollectorTest {

    private static final String TEST_PRODUCT = "RHEL";
    private static final Integer TEST_PRODUCT_ID = 1;

    private ApplicationClock clock;
    private ProductIdToProductsMapSource productIdToProductsMapSource;
    private RoleToProductsMapSource productToRolesMapSource;
    private List<String> rhelProducts;
    private InventoryRepository inventoryRepo;
    private FactNormalizer factNormalizer;
    private InventoryAccountUsageCollector collector;

    @BeforeEach
    public void setupTest() throws Exception {
        productIdToProductsMapSource = mock(ProductIdToProductsMapSource.class);
        productToRolesMapSource = mock(RoleToProductsMapSource.class);

        rhelProducts = Collections.singletonList("RHEL");

        when(productIdToProductsMapSource.getValue()).thenReturn(
            Collections.singletonMap(TEST_PRODUCT_ID, rhelProducts));
        when(productToRolesMapSource.getValue()).thenReturn(Collections.emptyMap());

        clock = new ApplicationClock();
        inventoryRepo = mock(InventoryRepository.class);
        factNormalizer = new FactNormalizer(new ApplicationProperties(), productIdToProductsMapSource,
            productToRolesMapSource, clock);
        collector = new InventoryAccountUsageCollector(factNormalizer, inventoryRepo);
    }

    @Test
    public void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() throws Exception {
        Collection<String> targetAccounts = Arrays.asList("A1", "A2");
        InventoryHostFacts host1 = createHost("A1", "O1", TEST_PRODUCT_ID, 4, 4);
        InventoryHostFacts host2 = createHost("A1", "O1", TEST_PRODUCT_ID, 8, 4);
        InventoryHostFacts host3 = createHost("A2", "O2", TEST_PRODUCT_ID, 2, 6);
        when(inventoryRepo.getFacts(eq(targetAccounts)))
            .thenReturn(Arrays.asList(host1, host2, host3).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts)
            .stream()
            .collect(Collectors.toMap(AccountUsageCalculation::getAccount, Function.identity()));
        assertEquals(2, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));
        assertThat(calcs, Matchers.hasKey("A2"));

        AccountUsageCalculation a1Calc = calcs.get("A1");
        assertEquals(1, a1Calc.getProducts().size());
        assertCalculation(a1Calc, "A1", "O1", "RHEL", 12, 8, 2);

        AccountUsageCalculation a2Calc = calcs.get("A2");
        assertEquals(1, a2Calc.getProducts().size());
        assertCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 2, 6, 1);
    }

    @Test
    void testTallyCoresAndSocketsOfRhelViaSystemProfileOnly() throws Exception {
        Collection<String> targetAccounts = Arrays.asList("A1", "A2");
        InventoryHostFacts host1 = createHost("A1", "O1", TEST_PRODUCT_ID, 0, 0, 1, 4);
        InventoryHostFacts host2 = createHost("A1", "O1", TEST_PRODUCT_ID, 0, 0, 2, 4);
        InventoryHostFacts host3 = createHost("A2", "O2", TEST_PRODUCT_ID, 0, 0, 2, 6);
        when(inventoryRepo.getFacts(eq(targetAccounts)))
            .thenReturn(Arrays.asList(host1, host2, host3).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts)
            .stream()
            .collect(Collectors.toMap(AccountUsageCalculation::getAccount, Function.identity()));
        assertEquals(2, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));
        assertThat(calcs, Matchers.hasKey("A2"));

        AccountUsageCalculation a1Calc = calcs.get("A1");
        assertEquals(1, a1Calc.getProducts().size());
        assertCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 12, 8, 2);

        AccountUsageCalculation a2Calc = calcs.get("A2");
        assertEquals(1, a2Calc.getProducts().size());
        assertCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 12, 6, 1);
    }

    @Test
    public void testCalculationDoesNotIncludeHostWhenProductDoesntMatch() throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");
        InventoryHostFacts h1 = createHost("A1", "Owner1", TEST_PRODUCT_ID, 8, 12);
        InventoryHostFacts h2 = createHost("A1", "Owner1", 32, 12, 14);
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts)
            .stream()
            .collect(Collectors.toMap(AccountUsageCalculation::getAccount, Function.identity()));
        assertEquals(1, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation accountCalc = calcs.get("A1");
        assertEquals(1, accountCalc.getProducts().size());
        assertCalculation(accountCalc, "A1", "Owner1", TEST_PRODUCT, 8, 12, 1);
    }

    @Test
    public void throwsISEOnAttemptToCalculateFactsBelongingToADifferentOwnerForSameAccount()
        throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");

        InventoryHostFacts h1 = createHost("A1", "Owner1", TEST_PRODUCT_ID, 1, 2);
        InventoryHostFacts h2 = createHost("A1", "Owner2", TEST_PRODUCT_ID, 1, 2);
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Throwable e = assertThrows(IllegalStateException.class,
            () -> collector.collect(rhelProducts, targetAccounts));

        String expectedMessage = String.format("Attempt to set a different owner for an account: %s:%s",
            "Owner1", "Owner2");
        assertEquals(expectedMessage, e.getMessage());
    }

    private InventoryHostFacts createHost(String account, String orgId, Integer product, int cores,
        int sockets) {
        return createHost(account, orgId, product, cores, sockets, 0, 0);
    }

    private InventoryHostFacts createHost(String account, String orgId, Integer product, int cores,
        int sockets, int systemProfileCoresPerSocket, int systemProfileSockets) {
        return new InventoryHostFacts(account, account + "_system", orgId, String.valueOf(cores),
            String.valueOf(sockets),
            StringUtils.collectionToCommaDelimitedString(Arrays.asList(product)),
            OffsetDateTime.now().toString(), String.valueOf(systemProfileCoresPerSocket),
            String.valueOf(systemProfileSockets), null, null, null, null);
    }

    private void assertCalculation(AccountUsageCalculation calc, String account, String owner,
        String product, int cores, int sockets, int instances) {
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
