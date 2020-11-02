/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;

import java.util.List;


public class ProductIdToProductsMapSourceTest {

    @Test
    public void ensureResourcePathComesFromApplicationProperty() throws Exception {
        ApplicationProperties props = new ApplicationProperties();
        props.setProductIdToProductsMapResourceLocation("classpath:test_product_id_to_products_map.yaml");

        ProductIdToProductsMapSource source = new ProductIdToProductsMapSource(props, new ApplicationClock());
        source.setResourceLoader(new FileSystemResourceLoader());
        source.init();

        List<String> prodList = source.getValue().get(1);
        assertEquals(1, prodList.size());

        assertThat(prodList, Matchers.contains("RHEL"));
    }

}
