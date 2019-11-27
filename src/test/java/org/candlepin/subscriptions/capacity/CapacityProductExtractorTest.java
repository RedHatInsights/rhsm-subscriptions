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
package org.candlepin.subscriptions.capacity;

import static org.hamcrest.MatcherAssert.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.FileSystemResourceLoader;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapacityProductExtractorTest {

    private CapacityProductExtractor extractor;
    private ApplicationClock clock = new ApplicationClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @BeforeAll
    void setup() throws IOException {
        ApplicationProperties props = new ApplicationProperties();
        props.setProductIdToProductsMapResourceLocation("classpath:test_product_id_to_products_map.yaml");
        props.setRoleToProductsMapResourceLocation("classpath:test_role_to_products_map.yaml");

        ProductIdToProductsMapSource productIdToProductsMapSource = new ProductIdToProductsMapSource(props,
            clock);
        productIdToProductsMapSource.setResourceLoader(new FileSystemResourceLoader());
        productIdToProductsMapSource.init();

        extractor = new CapacityProductExtractor(productIdToProductsMapSource);
    }

    @Test
    void productExtractorReturnsExpectedProducts() {
        Set<String> products = extractor.getProducts(Arrays.asList("6", "9", "10"));
        assertThat(products, Matchers.containsInAnyOrder("RHEL", "NOT RHEL", "RHEL Workstation",
            "RHEL Server"));
    }

    @Test
    void productExtractorReturnsNoProductsIfNoProductIdsMatch() {
        Set<String> products = extractor.getProducts(Collections.singletonList("42"));
        assertThat(products, Matchers.empty());
    }

    @Test
    void productSetIsInvalid() {
        Set<String> products = new HashSet<>();
        products.add("RHEL Workstation");
        products.add("Satellite 6");
        assertThat(extractor.setIsInvalid(products), Matchers.is(true));
    }

    @Test
    void productSetIsValid() {
        Set<String> products = new HashSet<>();
        products.add("RHEL Workstation");
        products.add("RHEL");
        products.add("RHEL for x86");
        assertThat(extractor.setIsInvalid(products), Matchers.is(false));
    }

    @Test
    void productExtractorReturnsExpectedProductsWhenSatellitePresent() {
        Set<String> products = extractor.getProducts(Arrays.asList("12"));
        assertThat(products, Matchers.containsInAnyOrder("Satellite 6 Capsule"));
    }
}
