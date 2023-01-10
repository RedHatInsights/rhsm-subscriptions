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
package org.candlepin.subscriptions.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.candlepin.subscriptions.umb.UmbOperationalProduct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"capacity-ingress", "test", "api"})
class OfferingWorkerTest {

  @Autowired OfferingWorker offeringWorker;
  @MockBean OfferingSyncController controller;

  @Test
  void testReceive() {
    // Given a SKU is allowlisted and retrievable from upstream,

    when(controller.syncOffering(anyString())).thenReturn(SyncResult.FETCHED_AND_SYNCED);

    // When an allowlisted SKU is received,
    String sku = "RH00604F5";
    offeringWorker.receive(new OfferingSyncTask(sku));

    // Then the offering should be synced.
    verify(controller).syncOffering(sku);
  }

  @Test
  void testReceive_WhenValidProductTopic() throws JsonProcessingException {
    String productMessageXml =
        "<?xml version=\"1.0\"?> <CanonicalMessage><Payload><Sync><OperationalProduct><Sku>RH0180191</Sku><SkuDescription>Test</SkuDescription><Role>test</Role><ProductRelationship><ParentProduct><Sku>RH0180191</Sku></ParentProduct><ChildProduct><Sku>SVCRH01</Sku></ChildProduct><ChildProduct><Sku>SVCRH01V4</Sku></ChildProduct></ProductRelationship><Attribute><Code>USAGE</Code><Name>Usage</Name><Value>Production</Value></Attribute></OperationalProduct></Sync></Payload></CanonicalMessage>";
    offeringWorker.receive(productMessageXml);
    verify(controller).syncUmbProduct(any(UmbOperationalProduct.class));
  }
}
