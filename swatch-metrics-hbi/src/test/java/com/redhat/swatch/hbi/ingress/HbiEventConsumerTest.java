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
package com.redhat.swatch.hbi.ingress;

import static org.mockito.Mockito.*;

import com.redhat.swatch.hbi.config.FeatureFlags;
import com.redhat.swatch.hbi.dto.HbiEvent;
import com.redhat.swatch.hbi.dto.HbiHostCreateUpdateEventDTO;
import com.redhat.swatch.hbi.processing.HbiHostEventHandler;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import java.util.Collections;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Disabled
public class HbiEventConsumerTest {

  @Mock private FeatureFlags featureFlags;

  @Mock private Emitter<Event> emitter;

  @Mock private HbiHostEventHandler eventHandler;

  @InjectMocks private HbiEventConsumer hbiEventConsumer;

  public HbiEventConsumerTest() {
    MockitoAnnotations.openMocks(this);
    this.hbiEventConsumer = new HbiEventConsumer(emitter, featureFlags, eventHandler);
  }

  @Test
  void testConsumeWithUnsupportedEventType() {
    HbiEvent unsupportedEvent = mock(HbiEvent.class);
    KafkaMessageMetadata<?> metadata = mock(KafkaMessageMetadata.class);

    when(unsupportedEvent.getType()).thenReturn("UnsupportedType");

    hbiEventConsumer.consume(unsupportedEvent, metadata);

    verifyNoInteractions(eventHandler, featureFlags, emitter);
  }

  @Test
  void testConsumeWithValidEventAndEmissionEnabled() {
    HbiHostCreateUpdateEventDTO validEvent = mock(HbiHostCreateUpdateEventDTO.class);
    KafkaMessageMetadata<?> metadata = mock(KafkaMessageMetadata.class);
    Event generatedEvent = mock(Event.class);

    // Mock handle method and feature flags
    when(eventHandler.handle(validEvent)).thenReturn(Collections.singletonList(generatedEvent));
    when(featureFlags.emitEvents()).thenReturn(true);

    // Ensure send() is properly mocked
    doNothing().when(emitter).send(any(Message.class));

    hbiEventConsumer.consume(validEvent, metadata);

    // Capture the message and validate
    ArgumentCaptor<Message<Event>> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(emitter, times(1)).send(messageCaptor.capture());
    Message<Event> capturedMessage = messageCaptor.getValue();

    // Ensure the payload matches
    assert capturedMessage.getPayload() == generatedEvent;
  }

  @Test
  void testConsumeWithValidEventAndEmissionDisabled() {
    HbiHostCreateUpdateEventDTO validEvent = mock(HbiHostCreateUpdateEventDTO.class);
    KafkaMessageMetadata<?> metadata = mock(KafkaMessageMetadata.class);
    Event generatedEvent = mock(Event.class);

    when(eventHandler.handle(validEvent)).thenReturn(Collections.singletonList(generatedEvent));
    when(featureFlags.emitEvents()).thenReturn(false);

    hbiEventConsumer.consume(validEvent, metadata);

    verify(emitter, never()).send(any(Message.class));
    verify(eventHandler, times(1)).handle(validEvent);
  }
}
