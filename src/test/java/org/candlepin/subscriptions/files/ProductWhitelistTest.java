/*
 * Copyright (c) 2019 Red Hat, Inc.
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
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;

import java.io.IOException;

class ProductWhitelistTest {

    @Test
    void testUnspecifiedLocationAllowsArbitraryProducts() throws IOException {
        ProductWhitelist whitelist = initProductWhitelist("");
        assertTrue(whitelist.productIdMatches("whee!"));
    }

    @Test
    void testAllowsProductsSpecified() throws IOException {
        ProductWhitelist whitelist = initProductWhitelist("classpath:item_per_line.txt");
        assertTrue(whitelist.productIdMatches("I1"));
        assertTrue(whitelist.productIdMatches("I2"));
        assertTrue(whitelist.productIdMatches("I3"));
    }

    @Test
    void testDisallowsProductsNotInWhitelist() throws IOException {
        ProductWhitelist whitelist = initProductWhitelist("classpath:item_per_line.txt");
        assertFalse(whitelist.productIdMatches("not on the list :-("));
    }

    private ProductWhitelist initProductWhitelist(String resourceLocation) throws IOException {
        ApplicationProperties props = new ApplicationProperties();
        props.setProductWhitelistResourceLocation(resourceLocation);
        ProductWhitelist whitelist = new ProductWhitelist(props, new ApplicationClock());
        whitelist.setResourceLoader(new FileSystemResourceLoader());
        whitelist.init();
        return whitelist;
    }
}
