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
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testQpcNormalization() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("RHEL", clock.now()),
            new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testSystemProfileNormalization() {
        InventoryHostFacts host = createSystemProfileHost(Collections.singletonList(1), 4, 2,
            clock.now());
        NormalizedFacts normalized = normalizer.normalize(host, new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(8), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testNormalizeNonRhelProduct() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(42), 4, 8, null,
            clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    void testSystemProfileNonRhelProduct() {
        NormalizedFacts normalized = normalizer.normalize(createSystemProfileHost(
            Collections.singletonList(42), 2, 4, clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(8), normalized.getCores());
        assertEquals(Integer.valueOf(4), normalized.getSockets());
    }

    @Test
    public void testSystemProfileInfrastructureType() {
        InventoryHostFacts baseFacts = createBaseHost("Account", "test-org");
        baseFacts.setSystemProfileInfrastructureType("virtual");
        baseFacts.setSyncTimestamp(clock.now().toString());

        NormalizedFacts normalized = normalizer.normalize(baseFacts, new HashMap<>());
        assertThat(normalized.isVirtual(), Matchers.is(true));
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlyCoresAreSet() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost((List<Integer>) null, 4, null,
            null, clock.now()), new HashMap<>());
        assertNotNull(normalized.getProducts());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(4), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenProductsMissingFromFactsAndOnlySocketsAreSet() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost((List<Integer>) null, null, 8,
            null, clock.now()), new HashMap<>());
        assertNotNull(normalized.getProducts());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(8), normalized.getSockets());
    }

    @Test
    public void testNormalizeWhenCoresAndSocketsMissingFromFacts() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1), null, null, null,
            clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(0), normalized.getCores());
        assertEquals(Integer.valueOf(0), normalized.getSockets());
    }

    @Test
    public void testIgnoresHostWhenLastSyncIsOutOfConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(2);
        InventoryHostFacts facts = createRhsmHost("A1", "O1", "1", 4, 8, null, lastSynced);

        NormalizedFacts normalized = normalizer.normalize(facts, new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.empty());
        assertNull(normalized.getCores());
    }

    @Test
    public void testIncludesHostWhenLastSyncIsWithinTheConfiguredThreshold() {
        OffsetDateTime lastSynced = clock.now().minusDays(1);
        InventoryHostFacts facts = createRhsmHost("A1", "O1", "1", 4, 8, null, lastSynced);

        NormalizedFacts normalized = normalizer.normalize(facts, new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(4), normalized.getCores());
    }

    @Test
    void testRhelFromQpcFacts() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("RHEL", clock.now()),
            new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
    }

    @Test
    public void testEmptyProductListWhenRhelNotPresent() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("EAP", clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.empty());
    }

    @Test
    public void testEmptyProductListWhenQpcProductsNotSet() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost(null, clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.empty());
    }

    @Test
    void testNullSocketsNormalizeToZero() {
        InventoryHostFacts host = createRhsmHost(Collections.emptyList(), 0, 0, null, clock.now());
        NormalizedFacts normalizedHost = normalizer.normalize(host, new HashMap<>());

        assertEquals(0, normalizedHost.getSockets().intValue());
    }

    @Test
    void testDetectsMultipleProductsBasedOnProductId() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1, 5, 7), 2, 2, null,
            clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "NOT RHEL",
            "RHEL Ungrouped"));
    }

    @Test
    void testDetectsProductFromSyspurposeRole() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Collections.emptyList(), 2, 2,
            "role1", clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    void testRhelUngroupedIfNoVariants() {
        NormalizedFacts normalized = normalizer.normalize(createQpcHost("RHEL", clock.now()),
            new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Ungrouped"));
    }

    @Test
    void variantFromSyspurposeWinsIfMultipleVariants() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(9, 10), 2, 2,
            "role1", clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    void nonNumericProductIdIgnored() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost("A1", "O1", "9,10,Foobar", 2, 2,
            "role1", clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.containsInAnyOrder("RHEL", "RHEL Server"));
    }

    @Test
    public void testNormalizationDiscardsRHELWhenSatelliteExists() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(2, 11), 12, 2, null,
            clock.now()), new HashMap<>());
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("Satellite 6"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testNormalizationDiscardsRHELWhenOpenShiftExists() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(2, 13), 12, 2, null,
            clock.now()), new HashMap<>());
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("OpenShift Container Platform"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    void testNormalizationDiscardsRHELForArchWhenSatelliteExists() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(2, 11, 6789), 12, 2,
            null, clock.now()), new HashMap<>());
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("Satellite 6"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testNormalizationDiscardsRHELWhenSatelliteExistsSameProduct() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(12), 12, 2, null,
            clock.now()), new HashMap<>());
        assertEquals(1, normalized.getProducts().size());
        assertThat(normalized.getProducts(), Matchers.hasItem("Satellite 6 Capsule"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(2), normalized.getSockets());
    }

    @Test
    public void testModulo2SocketNormalizationForHypervisors() {
        InventoryHostFacts hypervisor = createHypervisor("A1", "O1", 1, 12, 3);

        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(hypervisor.getSubscriptionManagerId(), null);

        NormalizedFacts normalized = normalizer.normalize(hypervisor, mappedHypervisors);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(4), normalized.getSockets());
    }

    @Test
    public void testModulo2SocketNormalizationForPhysicalHosts() {
        NormalizedFacts normalized = normalizer.normalize(createRhsmHost(Arrays.asList(1), 12, 3, null,
            clock.now()), new HashMap<>());
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(4), normalized.getSockets());
    }

    @Test
    public void testNoModulo2SocketNormalizationForGuests() {
        InventoryHostFacts guestFacts = createGuest("hyp-id", "A1", "O1", 1, 12, 3);
        assertTrue(guestFacts.isVirtual());

        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(guestFacts.getHypervisorUuid(), guestFacts.getHypervisorUuid());

        NormalizedFacts normalized = normalizer.normalize(guestFacts, mappedHypervisors);
        assertThat(normalized.getProducts(), Matchers.hasItem("RHEL"));
        assertEquals(Integer.valueOf(12), normalized.getCores());
        assertEquals(Integer.valueOf(3), normalized.getSockets());
    }

    @Test
    void testPhysicalNormalization() {
        InventoryHostFacts hostFacts = createBaseHost("A1", "O1");
        assertFalse(hostFacts.isVirtual());
        assertTrue(StringUtils.isEmpty(hostFacts.getHypervisorUuid()));
        assertTrue(StringUtils.isEmpty(hostFacts.getSatelliteHypervisorUuid()));

        NormalizedFacts normalized = normalizer.normalize(hostFacts, new HashMap<>());
        assertFalse(normalized.isHypervisor());
        assertFalse(normalized.isVirtual());
        assertEquals(HostHardwareType.PHYSICAL, normalized.getHardwareType());
    }

    @Test
    public void testIsHypervisorNormalization() {
        InventoryHostFacts facts = createHypervisor("A1", "O1", 1, 12, 3);
        assertFalse(facts.isVirtual());

        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(facts.getSubscriptionManagerId(), facts.getSubscriptionManagerId());

        NormalizedFacts normalized = normalizer.normalize(facts, mappedHypervisors);
        assertTrue(normalized.isHypervisor());
        assertEquals(HostHardwareType.PHYSICAL, normalized.getHardwareType());
        assertFalse(normalized.isVirtual());
    }

    @Test
    public void testIsGuestNormalization() {
        InventoryHostFacts facts = createGuest("hyp-id", "A1", "O1", 1, 12, 3);
        assertTrue(facts.isVirtual());

        HashMap<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(facts.getHypervisorUuid(), facts.getHypervisorUuid());

        NormalizedFacts normalized = normalizer.normalize(facts, mappedHypervisors);
        assertTrue(normalized.isVirtual());
        assertFalse(normalized.isHypervisorUnknown());
        assertFalse(normalized.isHypervisor());

        facts = createGuest(null, "A1", "O1", 1, 12, 3);
        assertTrue(facts.isVirtual());

        normalized = normalizer.normalize(facts, new HashMap<>());
        assertTrue(normalized.isVirtual());
        assertEquals(HostHardwareType.VIRTUALIZED, normalized.getHardwareType());
        assertTrue(normalized.isHypervisorUnknown());
        assertFalse(normalized.isHypervisor());
    }

    @Test
    public void testThatCloudProviderIsSet() {
        String expectedCloudProvider = "aws";
        InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
        baseFacts.setCloudProvider(expectedCloudProvider);

        NormalizedFacts normalized = normalizer.normalize(baseFacts, new HashMap<>());
        assertNotNull(normalized.getCloudProviderType());
        assertEquals(HardwareMeasurementType.AWS, normalized.getCloudProviderType());
    }

    @Test
    public void testThatCloudProviderIsNotSetIfNull() {
        NormalizedFacts normalized = normalizer.normalize(createBaseHost("A1", "O1"), new HashMap<>());
        assertNull(normalized.getCloudProviderType());
    }

    @Test
    public void testThatCloudProviderIsNotSetIfEmpty() {
        InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
        baseFacts.setCloudProvider("");

        NormalizedFacts normalized = normalizer.normalize(baseFacts, new HashMap<>());
        assertNull(normalized.getCloudProviderType());
    }

    @Test
    public void testThatUnsupportedCloudProviderIsNotSet() {
        String expectedCloudProvider = "unknown";
        InventoryHostFacts baseFacts = createBaseHost("A1", "O1");
        baseFacts.setCloudProvider(expectedCloudProvider);

        NormalizedFacts normalized = normalizer.normalize(baseFacts, new HashMap<>());
        assertNull(normalized.getCloudProviderType());
    }

    @Test
    public void testPhysicalClassification() {
        InventoryHostFacts physical = createRhsmHost(Arrays.asList(12), 12, 2, null, clock.now());
        NormalizedFacts facts = normalizer.normalize(physical, new HashMap<>());
        assertClassification(facts, false, true, false);
    }

    @Test
    public void testGuestWithMappedHypervisorClassification() {
        InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "A1", "O1", 1, 12, 3);

        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(guestWithMappedHypervisor.getHypervisorUuid(),
            guestWithMappedHypervisor.getHypervisorUuid());

        NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, mappedHypervisors);
        assertClassification(facts, false, false, true);
    }

    @Test
    public void testGuestWithUnmappedHypervisorClassification() {
        InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "A1", "O1", 1, 12, 3);

        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(guestWithMappedHypervisor.getHypervisorUuid(), null);

        NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, mappedHypervisors);
        assertClassification(facts, false, true, true);
    }

    @Test
    public void testGuestWithUnmappedHypervisorClassificationUsingSatelliteMapping() {
        InventoryHostFacts guestWithMappedHypervisor = createGuest("mapped-hyp-id", "A1", "O1", 1, 12, 3);
        guestWithMappedHypervisor.setHypervisorUuid(null);
        guestWithMappedHypervisor.setSatelliteHypervisorUuid("mapped-hyp-id");

        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(guestWithMappedHypervisor.getSatelliteHypervisorUuid(), null);

        NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, mappedHypervisors);
        assertClassification(facts, false, true, true);
    }

    @Test
    public void testGuestWithNullHypIdIsUnmappedHypervisorClassification() {
        InventoryHostFacts guestWithMappedHypervisor = createGuest(null, "A1", "O1", 1, 12, 3);

        NormalizedFacts facts = normalizer.normalize(guestWithMappedHypervisor, new HashMap<>());
        assertClassification(facts, false, true, true);
    }

    @Test
    public void testHypervisorClassificationWhenMapped() {
        InventoryHostFacts hypervisor = createHypervisor("A1", "O1", 1, 12, 3);
        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());

        NormalizedFacts facts = normalizer.normalize(hypervisor, mappedHypervisors);
        assertClassification(facts, true, true, false);
    }

    @Test
    public void testHypervisorClassificationWhenUnmapped() {
        InventoryHostFacts hypervisor = createHypervisor("A1", "O1", 1, 12, 3);
        Map<String, String> mappedHypervisors = new HashMap<>();
        mappedHypervisors.put(hypervisor.getSubscriptionManagerId(), null);

        NormalizedFacts facts = normalizer.normalize(hypervisor, mappedHypervisors);
        assertClassification(facts, true, true, false);
    }

    @Test
    void testSyspurposeUnitsSockets() {
        InventoryHostFacts facts = createBaseHost("A1", "O1");
        facts.setCores(4);
        facts.setSockets(2);
        facts.setSyspurposeUnits("Sockets");

        NormalizedFacts normalized = normalizer.normalize(facts, Collections.emptyMap());
        assertEquals(2, normalized.getSockets().longValue());
        assertEquals(0, normalized.getCores().longValue());
    }

    @Test
    void testSyspurposeUnitsCores() {
        InventoryHostFacts facts = createBaseHost("A1", "O1");
        facts.setCores(4);
        facts.setSockets(2);
        facts.setSyspurposeUnits("Cores/vCPU");

        NormalizedFacts normalized = normalizer.normalize(facts, Collections.emptyMap());
        assertEquals(0, normalized.getSockets().longValue());
        assertEquals(4, normalized.getCores().longValue());
    }

    @Test
    void testSyspurposeUnitsUnknown() {
        InventoryHostFacts facts = createBaseHost("A1", "O1");
        facts.setCores(4);
        facts.setSockets(2);
        facts.setSyspurposeUnits("Foobar");

        NormalizedFacts normalized = normalizer.normalize(facts, Collections.emptyMap());
        assertEquals(2, normalized.getSockets().longValue());
        assertEquals(4, normalized.getCores().longValue());
    }

    @Test
    void testSyspurposeUnitsUnspecified() {
        InventoryHostFacts facts = createBaseHost("A1", "O1");
        facts.setCores(4);
        facts.setSockets(2);

        NormalizedFacts normalized = normalizer.normalize(facts, Collections.emptyMap());
        assertEquals(2, normalized.getSockets().longValue());
        assertEquals(4, normalized.getCores().longValue());
    }

    private void assertClassification(NormalizedFacts check, boolean isHypervisor,
        boolean isHypervisorUnknown, boolean isVirtual) {
        assertEquals(isHypervisor, check.isHypervisor());
        assertEquals(isHypervisorUnknown, check.isHypervisorUnknown());
        assertEquals(isVirtual, check.isVirtual());
    }
}
