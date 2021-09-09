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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.junit.jupiter.api.Test;

class OfferingWorkerTest {

  @Test
  void testReceive() {
    // Given a SKU is allowlisted and retrievable from upstream,
    TaskQueueProperties props = mock(TaskQueueProperties.class);
    KafkaConsumerRegistry consumerReg = mock(KafkaConsumerRegistry.class);
    MeterRegistry meterReg = mock(MeterRegistry.class);
    OfferingSyncController controller = mock(OfferingSyncController.class);

    Offering expected = new Offering();
    when(controller.getUpstreamOffering(anyString())).thenReturn(Optional.of(expected));
    when(meterReg.timer(anyString())).thenReturn(mock(Timer.class));

    OfferingWorker subject = new OfferingWorker(props, consumerReg, meterReg, controller);

    // When an allowlisted SKU is received,
    subject.receive(new OfferingSyncTask("RH00604F5"));

    // Then the offering should be synced.
    verify(controller).getUpstreamOffering(anyString());
    verify(controller).syncOffering(expected);
  }

  @Test
  void testReceiveUnfetchable() {
    // Given a SKU is allowlisted, but isn't retrieveable from upstream for some reason,
    TaskQueueProperties props = mock(TaskQueueProperties.class);
    KafkaConsumerRegistry consumerReg = mock(KafkaConsumerRegistry.class);
    MeterRegistry meterReg = mock(MeterRegistry.class);
    OfferingSyncController controller = mock(OfferingSyncController.class);

    when(controller.getUpstreamOffering(anyString())).thenReturn(Optional.empty());
    when(meterReg.timer(anyString())).thenReturn(mock(Timer.class));

    OfferingWorker subject = new OfferingWorker(props, consumerReg, meterReg, controller);

    // When an allowlisted SKU is received,
    subject.receive(new OfferingSyncTask("RH00604F5"));

    // Then the offering should not be synced.
    verify(controller).getUpstreamOffering(anyString());
    verify(controller, never()).syncOffering(any());
  }
}
