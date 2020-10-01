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
package org.candlepin.subscriptions.orgsync.db;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.orgsync.db.model.OrgConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@Transactional
@TestPropertySource("classpath:/test.properties")
public class OrgConfigRepositoryTest {

    @Autowired
    private OrgConfigRepository repository;
    private Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Test
    public void saveAndUpdate() {
        OffsetDateTime creation = OffsetDateTime.now(clock);
        OffsetDateTime expectedUpdate = creation.plusDays(1);

        String org = "test-org";
        OrgConfig config = new OrgConfig(org);
        config.setOptInType(OptInType.JMX);
        config.setSyncEnabled(true);
        config.setCreated(creation);
        config.setUpdated(expectedUpdate);

        repository.saveAndFlush(config);

        OrgConfig found = repository.getOne(org);
        assertNotNull(found);
        assertEquals(config, found);

        found.setSyncEnabled(false);
        found.setOptInType(OptInType.API);
        repository.saveAndFlush(found);

        OrgConfig updated = repository.getOne(org);
        assertNotNull(updated);
        assertEquals(Boolean.FALSE, updated.getSyncEnabled());
        assertEquals(OptInType.API, updated.getOptInType());
    }

    @Test
    public void testDelete() {
        OrgConfig config = createConfig("an-org", true);
        repository.saveAndFlush(config);

        OrgConfig toDelete = repository.getOne(config.getOrgId());
        assertNotNull(toDelete);
        repository.delete(toDelete);
        repository.flush();

        assertEquals(0, repository.count());
    }

    @Test
    public void testFindOrgsWithEnabledSync() {
        repository.saveAll(Arrays.asList(
            createConfig("A1", true),
            createConfig("A2", true),
            createConfig("A3", false),
            createConfig("A4", false)
        ));
        repository.flush();

        List<String> orgsWithSync = repository.findSyncEnabledOrgs().collect(Collectors.toList());
        assertEquals(2, orgsWithSync.size());
        assertTrue(orgsWithSync.containsAll(Arrays.asList("A1", "A2")));
    }

    private OrgConfig createConfig(String org, boolean canSync) {
        OrgConfig config = new OrgConfig(org);
        config.setOptInType(OptInType.API);
        config.setSyncEnabled(canSync);
        config.setCreated(OffsetDateTime.now(clock));
        config.setUpdated(config.getCreated().plusDays(1));
        return config;
    }

}
