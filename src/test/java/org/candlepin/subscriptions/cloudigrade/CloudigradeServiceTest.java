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
package org.candlepin.subscriptions.cloudigrade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.cloudigrade.api.model.IdentityHeader;
import org.candlepin.subscriptions.cloudigrade.api.resources.ConcurrentApi;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Base64;

@SpringBootTest
@ActiveProfiles("worker,test")
class CloudigradeServiceTest {
    @MockBean
    ConcurrentApi concurrentApi;

    @Autowired
    CloudigradeService cloudigradeService;

    @Autowired
    ObjectMapper mapper;

    @Test
    void testHeaderEncodesCorrectly() throws ApiException, IOException {
        ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
        Base64.Decoder b64Decoder = Base64.getDecoder();

        cloudigradeService.listDailyConcurrentUsages("foo123", 10, 0, LocalDate.MIN, LocalDate.MAX);

        verify(concurrentApi).listDailyConcurrentUsages(header.capture(), eq(10), eq(0),
            eq(LocalDate.MIN), eq(LocalDate.MAX));
        IdentityHeader expected =
            mapper.readValue(b64Decoder.decode(header.getValue()), IdentityHeader.class);
        assertEquals("foo123", expected.getIdentity().getAccountNumber());
        assertTrue(expected.getIdentity().getUser().getIsOrgAdmin());
    }
}
