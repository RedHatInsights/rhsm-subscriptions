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
package org.candlepin.insights.orgsync.db;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;

import javax.transaction.Transactional;

@SpringBootTest
// The transactional annotation will rollback the transaction at the end of every test.
@Transactional
@TestPropertySource("classpath:/test.properties")
class OrganizationRepositoryTest {
    @Autowired
    private OrganizationRepository repository;

    @Test
    void testSave() {
        Organization org = new Organization("1");
        assertNotNull(repository.saveAndFlush(org));
    }

    @Test
    void testGetOrgIdMethod() {
        Organization org1 = new Organization("1");
        Organization org2 = new Organization("2");
        assertNotNull(repository.saveAll(Arrays.asList(org1, org2)));
        repository.flush();
        assertEquals(2, repository.getOrgIdList().size());
        assertThat(repository.getOrgIdList(), containsInAnyOrder("1", "2"));
    }

    @Test
    void testSaveDoesNotCreateDuplicateEntries() {
        Organization org = new Organization("1");
        Organization dupe = new Organization("1");
        assertNotNull(repository.saveAndFlush(org));
        repository.saveAndFlush(dupe);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void testSaveAllIgnoresDuplicateEntries() {
        Organization org = new Organization("1");
        Organization dupe = new Organization("1");
        assertNotNull(repository.saveAll(Arrays.asList(org, dupe)));
        repository.flush();
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void testExistsById() {
        Organization org = new Organization("1");
        assertNotNull(repository.saveAndFlush(org));
        assertTrue(repository.existsById("1"));
        assertFalse(repository.existsById("2"));
    }
}
