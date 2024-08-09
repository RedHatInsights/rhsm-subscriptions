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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.swatch.contract.model.OfferingSyncTask;
import com.redhat.swatch.contract.model.SyncResult;
import com.redhat.swatch.contract.test.resources.EnableUmbResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(EnableUmbResource.class)
class OfferingSyncTaskConsumerTest {
  @InjectMock OfferingSyncService service;
  @Inject OfferingSyncTaskConsumer consumer;

  @Test
  void testConsumeFromTopic() {
    // Given a SKU is not denied and retrievable from upstream,
    when(service.syncOffering(anyString())).thenReturn(SyncResult.FETCHED_AND_SYNCED);

    // When a non denylisted SKU is received,
    String sku = "RH00604F5";
    consumer.consumeFromTopic(new OfferingSyncTask(sku));

    // Then the offering should be synced.
    verify(service).syncOffering(sku);
  }

  @Test
  void testOfferingSyncFailure() {
    when(service.syncOffering(anyString())).thenThrow(new RuntimeException());
    consumer.consumeFromTopic(new OfferingSyncTask("SKU"));
  }

  @Test
  void testConsumeFromUmb_WhenValidProductTopic() throws JsonProcessingException {
    String productMessageXml =
        "<?xml version=\"1.0\"?> <CanonicalMessage><Payload><Sync><OperationalProduct><Sku>RH0180191</Sku><SkuDescription>Test</SkuDescription><Role>test</Role><ProductRelationship><ParentProduct><Sku>RH0180191</Sku></ParentProduct><ChildProduct><Sku>SVCRH01</Sku></ChildProduct><ChildProduct><Sku>SVCRH01V4</Sku></ChildProduct></ProductRelationship><Attribute><Code>USAGE</Code><Name>Usage</Name><Value>Production</Value></Attribute></OperationalProduct></Sync></Payload></CanonicalMessage>";
    consumer.consumeFromUmb(productMessageXml);
    verify(service).syncUmbProductFromXml(productMessageXml);
  }
}
