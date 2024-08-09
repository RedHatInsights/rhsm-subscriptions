/*
 * Copyright Red Hat, Inc.
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
package com.redhat.swatch.contract.service;

import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Collections;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionServiceTest {

  @InjectMock @RestClient SearchApi searchApi;

  @Inject SubscriptionService subject;

  @Test
  void verifySearchByOrgIdTest() throws ApiException {
    when(searchApi.searchSubscriptionsByOrgId("123", 0, 1)).thenReturn(Collections.emptyList());
    subject.getSubscriptionsByOrgId("123", 0, 1);
    verify(searchApi, only()).searchSubscriptionsByOrgId("123", 0, 1);
  }

  @Test
  void verifyGetByIdTest() throws ApiException {
    when(searchApi.getSubscriptionById("123")).thenReturn(new Subscription());
    subject.getSubscriptionById("123");
    verify(searchApi, only()).getSubscriptionById("123");
  }
}
