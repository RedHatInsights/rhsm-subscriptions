/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.subscription;

import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.subscription.api.resources.SearchApi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

@SpringBootTest
@ActiveProfiles("worker,test")
class SubscriptionServiceTest {
    private static final String CRITERIA =
        "criteria;web_customer_id=123;statusList=active;statusList=temporary";
    private static final String OPTIONS = "options;products=ALL";

    @MockBean
    SearchApi searchApi;

    @Autowired
    SubscriptionService subject;

    @Test
    void verifyCallIsMadeCorrectlyTest() throws ApiException {
        when(searchApi
            .searchSubscriptions(CRITERIA, OPTIONS)).thenReturn(Collections.emptyList());
        subject.getSubscriptions("123");
        verify(searchApi, only()).searchSubscriptions(CRITERIA, OPTIONS);
    }
}
