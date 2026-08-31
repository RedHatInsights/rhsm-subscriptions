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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.contract.openapi.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.LoggerCaptor;
import com.redhat.swatch.contract.test.resources.DisableUmbResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DisableUmbResource.class)
class ProductStatusUMBMessageConsumerWhenUmbDisabledTest {

  @InjectMock OfferingSyncService offeringSyncService;
  @InjectSpy ProductStatusUMBMessageConsumer consumer;

  @BeforeAll
  static void configureLogging() {
    LoggerCaptor.registerHandler(ProductStatusUMBMessageConsumer.class);
  }

  @BeforeEach
  void setUp() {
    LoggerCaptor.clearRecords();
  }

  @Test
  void shouldIgnoreMessagesWhenUmbIsNotEnabled() {
    consumer.consumeMessage(ProductStatusUMBMessageConsumerTest.VALID_JSON_MESSAGE);
    verify(consumer, never())
        .consumeProduct(ProductStatusUMBMessageConsumerTest.VALID_JSON_MESSAGE);
    verify(offeringSyncService, never()).syncProductFromEvent(any(OperationalProductEvent.class));
  }
}
