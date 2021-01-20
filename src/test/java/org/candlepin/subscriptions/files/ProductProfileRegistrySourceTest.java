/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.files;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;

import java.util.Arrays;
import java.util.HashSet;

class ProductProfileRegistrySourceTest {

    @Test
    void deserializesYaml() throws Exception {
        ProductProfileRegistrySource source =
            initRegistrySource("classpath:test_product_profile_registry.yaml");
        ProductProfileRegistry registry = source.getValue();

        HashSet<String> expected = new HashSet<>(Arrays.asList(
            "RHELProduct",
            "SatelliteProduct",
            "OpenShiftHourlyProduct",
            "OtherProduct"
        ));
        assertEquals(expected, registry.listProfiles());
    }

    private ProductProfileRegistrySource initRegistrySource(String resourceLocation) {
        ApplicationProperties props = new ApplicationProperties();
        props.setProductProfileRegistryResourceLocation(resourceLocation);
        ProductProfileRegistrySource source = new ProductProfileRegistrySource(props, new ApplicationClock());
        source.setResourceLoader(new FileSystemResourceLoader());
        source.init();
        return source;
    }
}
