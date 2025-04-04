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
package com.redhat.swatch.hbi.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.redhat.swatch.hbi.config.FeatureFlags;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@Disabled
public class EventsResourceTest {

  private Emitter<Event> emitter;
  private FeatureFlags flags;
  private EventsResource resource;

  @BeforeEach
  void setup() {
    emitter = mock(Emitter.class);
    flags = mock(FeatureFlags.class);
    resource = new EventsResource(emitter, flags);
  }

  @Test
  void shouldSendSwatchEventWhenEmittingIsEnabled() {
    when(flags.emitEvents()).thenReturn(true);

    ArgumentCaptor<Message<Event>> messageCaptor = ArgumentCaptor.forClass(Message.class);
    doNothing().when(emitter).send(messageCaptor.capture());

    String result = resource.sendSwatchEvent();

    assertEquals("Event sent!", result);
    Message<Event> sentMessage = messageCaptor.getValue();
    assertNotNull(sentMessage);
    assertEquals("Instance ID", sentMessage.getPayload().getInstanceId());
  }

  @Test
  void shouldNotSendEventWhenEmittingIsDisabled() {
    when(flags.emitEvents()).thenReturn(false);

    String result = resource.sendSwatchEvent();

    assertEquals("Not sending event since emitting events is disabled!", result);
    verify(emitter, never()).send(any(Message.class));
  }

  @Test
  void eventHasRequiredFieldsWhenEmitted() {
    when(flags.emitEvents()).thenReturn(true);

    ArgumentCaptor<Message<Event>> captor = ArgumentCaptor.forClass(Message.class);
    doNothing().when(emitter).send(captor.capture());

    resource.sendSwatchEvent();

    Event event = captor.getValue().getPayload();
    assertNotNull(event);
    assertEquals("Instance ID", event.getInstanceId());
  }
}
