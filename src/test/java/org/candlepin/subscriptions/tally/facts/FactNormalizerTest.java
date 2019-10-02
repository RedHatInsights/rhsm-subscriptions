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
package org.candlepin.subscriptions.tally.facts;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.ClassifiedInventoryHostFacts;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@TestInstance(Lifecycle.PER_CLASS)
@SpringBootTest
@TestPropertySource("classpath:/test.properties")
@Import(FixedClockConfiguration.class)
public class FactNormalizerTest {

    private FactNormalizer normalizer;

    @Autowired private ApplicationClock clock;

    @MockBean
    BuildProperties buildProperties;

    @BeforeAll
    public void setup() throws IOException {
        ApplicationProperties props = new ApplicationProperties();
        props.setProductIdToProductsMapResourceLocation("classpath:test_product_id_to_products_map.yaml");
        props.setRoleToProductsMapResourceLocation("classpath:test_role_to_products_map.yaml");

        ProductIdToProductsMapSource productIdToProductsMapSource = new ProductIdToProductsMapSource(props);
        productIdToProductsMapSource.setResourceLoader(new FileSystemResourceLoader());
        productIdToProductsMapSource.init();

        RoleToProductsMapSource productToRolesMapSource = new RoleToProductsMapSource(props);
        productToRolesMapSource.setResourceLoader(new FileSystemResourceLoader());
        productToRolesMapSource.init();

        normalizer = new FactNormalizer(new ApplicationProperties(), productIdToProductsMapSource,
            productToRolesMapSource, clock);
    }

    @Test
    public void testRhsmNormalization() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1), 12, 2, null));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testQpcNormalization() {
        ClassifiedInventoryHostFacts host = createQpcHost("RHEL");
        NormalizedFacts normalized = normalizer.normalize(host);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testSystemProfileNormalization() {
        ClassifiedInventoryHostFacts host = createSystemProfileHost(Collections.singletonList(1), 4, 2);
        NormalizedFacts normalized = normalizer.normalize(host);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(8), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testNormalizeNonRhelProduct() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(42), 4, 8, null));
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    void testSystemProfileNonRhelProduct() {
        NormalizedFacts normalized =
            normalizer.normalize(createSystemProfileHost(Collections.singletonList(42), 2, 4));
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(8), normalized.getCores());
        assertEquals(Integer.valueOf(4), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlyCoresAreSet() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost((List<Integer>) null, 4, null,
            null));
        assertNotNull(normalized.getProducts());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlySocketsAreSet() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost((List<Integer>) null, null, 8,
            null));
        assertNotNull(normalized.getProducts());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenCoresAndSocketsMissingFromFacts() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1), null, null, null));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testIgnoresHostWhenLastSyncIsOutOfConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(2);
        ClassifiedInventoryHostFacts facts =
            createRhsmHost("1", 4, 8, null, lastSynced.toString());

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertThat(normalized.getProducts(), Matchers.empty());
        assertNull(normalized.getCores());
    }

    @Test
    public void testIncludesHostWhenLastSyncIsWithinTheConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(1);
        ClassifiedInventoryHostFacts facts =
            createRhsmHost("1", 4, 8, null, lastSynced.toString());

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(4), normalized.getCores());
    }

    @Test
    void testRhelFromQpcFacts() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("RHEL"));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
    }

    @Test
    public void testEmptyProductListWhenRhelNotPresent() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("EAP"));
        assertThat(normalized.getProducts(), Matchers.empty());
    }

    @Test
    public void testEmptyProductListWhenQpcProductsNotSet() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost(null));
        assertThat(normalized.getProducts(), Matchers.empty());
    }

    @Test
    void testNullSocketsNormalizeToZero() {
        ClassifiedInventoryHostFacts host = createRhsmHost(Collections.emptyList(), 0, 0, null);

        NormalizedFacts normalizedHost = normalizer.normalize(host);

        assertEquals(0, normalizedHost.getSockets().intValue());
    }

    @Test
    void testDetectsMultipleProductsBasedOnProductId() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1, 5, 7), 2, 2, null));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "NOT RHEL",
            "RHEL Ungrouped"));
    }

    @Test
    void testDetectsProductFromSyspurposeRole() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Collections.emptyList(), 2, 2,
            "role1"));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    void testRhelUngroupedIfNoVariants() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("RHEL"));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Ungrouped"));
    }

    @Test
    void variantFromSyspurposeWinsIfMultipleVariants() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(9, 10), 2, 2,
            "role1"));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    void nonNumericProductIdIgnored() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost("9,10,Foobar", 2, 2,
            "role1"));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    public void testNormalizationDiscardsRHELWhenSatelliteExists() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(2, 11), 12, 2, null));
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("Satellite 6"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testNormalizationDiscardsRHELWhenSatelliteExistsSameProduct() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(12), 12, 2, null));
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("Satellite 6 Capsule"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    private ClassifiedInventoryHostFacts createRhsmHost(List<Integer> products, Integer cores,
        Integer sockets, String syspurposeRole) {
        return createRhsmHost(StringUtils.collectionToCommaDelimitedString(products), cores, sockets,
            syspurposeRole, clock.now().toString());
    }

    private ClassifiedInventoryHostFacts createRhsmHost(String products, Integer cores,
        Integer sockets, String syspurposeRole) {
        return createRhsmHost(products, cores, sockets, syspurposeRole, clock.now().toString());
    }

    private ClassifiedInventoryHostFacts createRhsmHost(String products, Integer cores,
        Integer sockets, String syspurposeRole, String syncTimeStamp) {

        InventoryHostFacts baseFacts = new InventoryHostFacts(
            "Account",
            "Test System",
            "test_org",
            String.valueOf(cores),
            String.valueOf(sockets),
            products,
            syncTimeStamp,
            null,
            null,
            null,
            null,
            null,
            syspurposeRole,
            "false",
            null,
            null,
            UUID.randomUUID().toString()
        );
        return new ClassifiedInventoryHostFacts(baseFacts);
    }

    private ClassifiedInventoryHostFacts createQpcHost(String qpcProducts) {
        InventoryHostFacts baseFacts = new InventoryHostFacts(
            "Account",
            "Test System",
            "test_org",
            null,
            null,
            null,
            clock.now().toString(),
            qpcProducts,
            null,
            qpcProducts,
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

    private ClassifiedInventoryHostFacts createSystemProfileHost(List<Integer> products,
        Integer coresPerSocket, Integer sockets) {
        InventoryHostFacts baseFacts = new InventoryHostFacts(
            "Account",
            "Test System",
            "test_org",
            null,
            null,
            null,
            clock.now().toString(),
            String.valueOf(coresPerSocket),
            String.valueOf(sockets),
            null,
            null,
            StringUtils.collectionToCommaDelimitedString(products),
            null,
            "false",
            null,
            null,
            UUID.randomUUID().toString()
        );
        return new ClassifiedInventoryHostFacts(baseFacts);
    }
}
