/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally.files;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.YamlFileSource;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Loads the product ID to list of Tally products mapping from a YAML file.
 */
@Component
public class ArchToProductMapSource extends YamlFileSource<Map<String, String>> {

    public ArchToProductMapSource(ApplicationProperties properties, ApplicationClock clock) {
        super(properties.getArchToProductMapResourceLocation(), clock.getClock(),
            properties.getArchToProductMapCacheTtl());
    }

    @Override
    protected Map<String, String> getDefault() {
        return Collections.emptyMap();
    }
}
