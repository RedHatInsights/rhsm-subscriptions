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

import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
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

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        ProductIdToProductsMapSource productIdToProductsMapSource = new ProductIdToProductsMapSource(props,
            clock);
        productIdToProductsMapSource.setResourceLoader(new FileSystemResourceLoader());
        productIdToProductsMapSource.init();

        RoleToProductsMapSource productToRolesMapSource = new RoleToProductsMapSource(props, clock);
        productToRolesMapSource.setResourceLoader(new FileSystemResourceLoader());
        productToRolesMapSource.init();

        normalizer = new FactNormalizer(new ApplicationProperties(), productIdToProductsMapSource,
            productToRolesMapSource, clock);
    }

    @Test
    public void testRhsmNormalization() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1), 12, 2, null,
            clock.now()));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testQpcNormalization() {
        ClassifiedInventoryHostFacts host = createQpcHost("RHEL", clock.now());
        NormalizedFacts normalized = normalizer.normalize(host);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testSystemProfileNormalization() {
        ClassifiedInventoryHostFacts host = createSystemProfileHost(Collections.singletonList(1), 4, 2,
            clock.now());
        NormalizedFacts normalized = normalizer.normalize(host);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(8), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testNormalizeNonRhelProduct() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(42), 4, 8, null,
            clock.now()));
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    void testSystemProfileNonRhelProduct() {
        NormalizedFacts normalized =
            normalizer.normalize(createSystemProfileHost(Collections.singletonList(42), 2, 4, clock.now()));
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(8), normalized.getCores());
        assertEquals(Integer.valueOf(4), normalized.getSockets());
    }

    @Test
    public void testSystemProfileInfrastructureType() {
        InventoryHostFacts baseFacts = createBaseHost("Account", "test-org");
        baseFacts.setSystemProfileInfrastructureType("virtual");
        baseFacts.setSyncTimestamp(clock.now().toString());
        ClassifiedInventoryHostFacts systemProfileHost = new ClassifiedInventoryHostFacts(baseFacts);
        NormalizedFacts normalized = normalizer.normalize(systemProfileHost);
        assertThat(normalized.isVirtual(), Matchers.is(true));
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlyCoresAreSet() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost((List<Integer>) null, 4, null,
            null, clock.now()));
        assertNotNull(normalized.getProducts());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlySocketsAreSet() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost((List<Integer>) null, null, 8,
            null, clock.now()));
        assertNotNull(normalized.getProducts());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenCoresAndSocketsMissingFromFacts() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1), null, null, null,
            clock.now()));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testIgnoresHostWhenLastSyncIsOutOfConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(2);
        ClassifiedInventoryHostFacts facts =
            createRhsmHost("A1", "O1", "1", 4, 8, null, lastSynced);

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertThat(normalized.getProducts(), Matchers.empty());
        assertNull(normalized.getCores());
    }

    @Test
    public void testIncludesHostWhenLastSyncIsWithinTheConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(1);
        ClassifiedInventoryHostFacts facts =
            createRhsmHost("A1", "O1", "1", 4, 8, null, lastSynced);

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(4), normalized.getCores());
    }

    @Test
    void testRhelFromQpcFacts() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("RHEL", clock.now()));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
    }

    @Test
    public void testEmptyProductListWhenRhelNotPresent() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("EAP", clock.now()));
        assertThat(normalized.getProducts(), Matchers.empty());
    }

    @Test
    public void testEmptyProductListWhenQpcProductsNotSet() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost(null, clock.now()));
        assertThat(normalized.getProducts(), Matchers.empty());
    }

    @Test
    void testNullSocketsNormalizeToZero() {
        ClassifiedInventoryHostFacts host = createRhsmHost(Collections.emptyList(), 0, 0, null, clock.now());

        NormalizedFacts normalizedHost = normalizer.normalize(host);

        assertEquals(0, normalizedHost.getSockets().intValue());
    }

    @Test
    void testDetectsMultipleProductsBasedOnProductId() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1, 5, 7), 2, 2, null,
            clock.now()));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "NOT RHEL",
            "RHEL Ungrouped"));
    }

    @Test
    void testDetectsProductFromSyspurposeRole() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Collections.emptyList(), 2, 2,
            "role1", clock.now()));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    void testRhelUngroupedIfNoVariants() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("RHEL", clock.now()));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Ungrouped"));
    }

    @Test
    void variantFromSyspurposeWinsIfMultipleVariants() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(9, 10), 2, 2,
            "role1", clock.now()));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    void nonNumericProductIdIgnored() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost("A1", "O1", "9,10,Foobar", 2, 2,
            "role1", clock.now()));
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    public void testNormalizationDiscardsRHELWhenSatelliteExists() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(2, 11), 12, 2, null,
            clock.now()));
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("Satellite 6"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testNormalizationDiscardsRHELWhenSatelliteExistsSameProduct() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(12), 12, 2, null,
            clock.now()));
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("Satellite 6 Capsule"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testModulo2SocketNormalizationForHypervisors() {
        NormalizedFacts normalized = normalizer.normalize(createHypervisor("A1", "O1", 1, 12, 3));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(4), normalized.getSockets());
    }

    @Test
    public void testModulo2SocketNormalizationForPhysicalHosts() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1), 12, 3, null,
            clock.now()));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(4), normalized.getSockets());
    }

    @Test
    public void testNoModulo2SocketNormalizationForGuests() {
        NormalizedFacts normalized = normalizer.normalize(createGuest("hyp-id", "A1", "O1", 1, 12, 3));
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(3), normalized.getSockets());
    }

    @Test
    public void testIsHypervisorNormalization() {
        ClassifiedInventoryHostFacts facts = createHypervisor("A1", "O1", 1, 12, 3);
        assertTrue(facts.isHypervisor());
        assertFalse(facts.isVirtual());

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertTrue(normalized.isHypervisor());
        assertFalse(normalized.isVirtual());
    }

    @Test
    public void testIsGuestNormalization() {
        ClassifiedInventoryHostFacts facts = createGuest("hyp-id", "A1", "O1", 1, 12, 3);
        assertTrue(facts.isVirtual());
        assertFalse(facts.isHypervisorUnknown());
        assertFalse(facts.isHypervisor());

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertTrue(normalized.isVirtual());
        assertFalse(normalized.isHypervisorUnknown());
        assertFalse(normalized.isHypervisor());

        facts = createGuest(null, "A1", "O1", 1, 12, 3);
        assertTrue(facts.isVirtual());
        assertTrue(facts.isHypervisorUnknown());
        assertFalse(facts.isHypervisor());

        normalized = normalizer.normalize(facts);
        assertTrue(normalized.isVirtual());
        assertTrue(normalized.isHypervisorUnknown());
        assertFalse(normalized.isHypervisor());
    }

    @Test
    public void testThatCloudProviderIsSet() {
        String expectedCloudProvider = "aws";
        InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
        baseFacts.setCloudProvider(expectedCloudProvider);
        ClassifiedInventoryHostFacts facts = new ClassifiedInventoryHostFacts(baseFacts);

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertNotNull(normalized.getCloudProviderType());
        assertEquals(HardwareMeasurementType.AWS, normalized.getCloudProviderType());

    }

    @Test
    public void testThatCloudProviderIsNotSetIfNull() {
        ClassifiedInventoryHostFacts facts = new ClassifiedInventoryHostFacts(createBaseHost("A1", "O1"));

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertNull(normalized.getCloudProviderType());
    }

    @Test
    public void testThatCloudProviderIsNotSetIfEmpty() {
        InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
        baseFacts.setCloudProvider("");
        ClassifiedInventoryHostFacts facts = new ClassifiedInventoryHostFacts(baseFacts);

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertNull(normalized.getCloudProviderType());
    }

    @Test
    public void testThatUnsupportedCloudProviderIsNotSet() {
        String expectedCloudProvider = "unknown";
        InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
        baseFacts.setCloudProvider(expectedCloudProvider);
        ClassifiedInventoryHostFacts facts = new ClassifiedInventoryHostFacts(baseFacts);

        NormalizedFacts normalized = normalizer.normalize(facts);
        assertNull(normalized.getCloudProviderType());
    }
}
