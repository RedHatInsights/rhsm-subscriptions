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

import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class InventoryAccountUsageCollectorTest {

    private static final String TEST_PRODUCT = "RHEL";
    private static final Integer TEST_PRODUCT_ID = 1;
    private static List<String> rhelProducts = Collections.singletonList(TEST_PRODUCT);

    @MockBean private ClassificationProxyRepository inventoryRepo;
    @Autowired private InventoryAccountUsageCollector collector;

    /**
     * Why are we doing this?  Because when we use a MockBean annotation on the MapSources, we
     * don't get access to the mock until an @BeforeEach method. However, we need to mock the
     * getValue() call before that so the FactNormalizer gets a populated list when it is constructed.
     * The solution is to replace the bean definition of the MapSource with the ones below.
     */
    @TestConfiguration
    static class TestContextConfiguration {
        @Bean
        @Primary
        public ProductIdToProductsMapSource testProductIdToProductsMapSource() throws IOException {
            ProductIdToProductsMapSource source = mock(ProductIdToProductsMapSource.class);
            when(source.getValue()).thenReturn(
                Collections.singletonMap(TEST_PRODUCT_ID, rhelProducts));
            return source;
        }

        @Bean
        @Primary
        public RoleToProductsMapSource testRoleToProducsMapSource() throws IOException {
            RoleToProductsMapSource source = mock(RoleToProductsMapSource.class);
            when(source.getValue()).thenReturn(Collections.emptyMap());
            return source;
        }
    }

    @Test
    public void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() throws Exception {
        Collection<String> targetAccounts = Arrays.asList("A1", "A2");
        ClassifiedInventoryHostFacts host1 = createHost("A1", "O1", TEST_PRODUCT_ID, 4, 4);
        ClassifiedInventoryHostFacts host2 = createHost("A1", "O1", TEST_PRODUCT_ID, 8, 4);
        ClassifiedInventoryHostFacts host3 = createHost("A2", "O2", TEST_PRODUCT_ID, 2, 6);
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
        ClassifiedInventoryHostFacts host1 = createHost("A1", "O1", TEST_PRODUCT_ID, 0, 0, 1, 4);
        ClassifiedInventoryHostFacts host2 = createHost("A1", "O1", TEST_PRODUCT_ID, 0, 0, 2, 4);
        ClassifiedInventoryHostFacts host3 = createHost("A2", "O2", TEST_PRODUCT_ID, 0, 0, 2, 6);
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
        ClassifiedInventoryHostFacts h1 = createHost("A1", "Owner1", TEST_PRODUCT_ID, 8, 12);
        ClassifiedInventoryHostFacts h2 = createHost("A1", "Owner1", 32, 12, 14);
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

        ClassifiedInventoryHostFacts h1 = createHost("A1", "Owner1", TEST_PRODUCT_ID, 1, 2);
        ClassifiedInventoryHostFacts h2 = createHost("A1", "Owner2", TEST_PRODUCT_ID, 1, 2);
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Throwable e = assertThrows(IllegalStateException.class,
            () -> collector.collect(rhelProducts, targetAccounts));

        String expectedMessage = String.format("Attempt to set a different owner for an account: %s:%s",
            "Owner1", "Owner2");
        assertEquals(expectedMessage, e.getMessage());
    }

    private ClassifiedInventoryHostFacts createHost(String account, String orgId, Integer product, int cores,
        int sockets) {
        return createHost(account, orgId, product, cores, sockets, 0, 0);
    }

    private ClassifiedInventoryHostFacts createHost(String account, String orgId, Integer product, int cores,
        int sockets, int systemProfileCoresPerSocket, int systemProfileSockets) {
        InventoryHostFacts baseFacts = new InventoryHostFacts(
            account,
            account + "_system",
            orgId,
            String.valueOf(cores),
            String.valueOf(sockets),
            StringUtils.collectionToCommaDelimitedString(Arrays.asList(product)),
            OffsetDateTime.now().toString(),
            String.valueOf(systemProfileCoresPerSocket),
            String.valueOf(systemProfileSockets),
            null,
            null,
            null,
            null,
            "false",
            null,
            null,
            UUID.randomUUID().toString()
        );

        return new ClassifiedInventoryHostFacts(baseFacts);
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
