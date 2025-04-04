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
package com.redhat.swatch.hbi.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.domain.HbiHostManager;
import com.redhat.swatch.hbi.domain.filtering.HostEventFilter;
import com.redhat.swatch.hbi.domain.normalization.FactNormalizer;
import com.redhat.swatch.hbi.domain.normalization.Host;
import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import com.redhat.swatch.hbi.dto.HbiHost;
import com.redhat.swatch.hbi.dto.HbiHostCreateUpdateEventDTO;
import com.redhat.swatch.hbi.egress.SwatchEventBuilder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class HbiHostEventHandlerTest {

  @Disabled
  @Test
  public void testHandleWithEmptyHost() {
    FactNormalizer factNormalizer = mock(FactNormalizer.class);
    HbiHostManager hbiHostManager = mock(HbiHostManager.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    HostEventFilter hbiEventFilter = mock(HostEventFilter.class);
    SwatchEventBuilder swatchEventBuilder = mock(SwatchEventBuilder.class);
    HbiHostEventHandler eventHandler =
        new HbiHostEventHandler(
            factNormalizer, hbiHostManager, objectMapper, hbiEventFilter, swatchEventBuilder);

    HbiHostCreateUpdateEventDTO eventDTO = new HbiHostCreateUpdateEventDTO();

    List<Event> result = eventHandler.handle(eventDTO);

    assertTrue(result.isEmpty());
    verifyNoInteractions(hbiEventFilter, factNormalizer, hbiHostManager, swatchEventBuilder);
  }

  @Disabled
  @Test
  public void testHandleWithNullTimestamp() {
    FactNormalizer factNormalizer = mock(FactNormalizer.class);
    HbiHostManager hbiHostManager = mock(HbiHostManager.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    HostEventFilter hbiEventFilter = mock(HostEventFilter.class);
    SwatchEventBuilder swatchEventBuilder = mock(SwatchEventBuilder.class);
    HbiHostEventHandler eventHandler =
        new HbiHostEventHandler(
            factNormalizer, hbiHostManager, objectMapper, hbiEventFilter, swatchEventBuilder);

    HbiHostCreateUpdateEventDTO eventDTO = new HbiHostCreateUpdateEventDTO();
    HbiHost host = new HbiHost();
    eventDTO.setHost(host);

    when(hbiEventFilter.shouldSkip(any(Host.class), any(OffsetDateTime.class))).thenReturn(false);

    List<Event> result = eventHandler.handle(eventDTO);

    assertTrue(result.isEmpty());
    verify(hbiEventFilter, times(1)).shouldSkip(any(Host.class), any(OffsetDateTime.class));
    verifyNoInteractions(factNormalizer, hbiHostManager, swatchEventBuilder);
  }

  @Test
  public void testHandleWithNormalizationAsHypervisor() throws JsonProcessingException {
    FactNormalizer factNormalizer = mock(FactNormalizer.class);
    HbiHostManager hbiHostManager = mock(HbiHostManager.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    HostEventFilter hbiEventFilter = mock(HostEventFilter.class);
    SwatchEventBuilder swatchEventBuilder = mock(SwatchEventBuilder.class);
    HbiHostEventHandler eventHandler =
        new HbiHostEventHandler(
            factNormalizer, hbiHostManager, objectMapper, hbiEventFilter, swatchEventBuilder);

    HbiHostCreateUpdateEventDTO eventDTO = new HbiHostCreateUpdateEventDTO();
    HbiHost host = new HbiHost();
    host.setStaleTimestamp(OffsetDateTime.now().toString());
    eventDTO.setHost(host);

    NormalizedFacts normalizedFacts = mock(NormalizedFacts.class);
    when(normalizedFacts.isHypervisor()).thenReturn(true);
    when(hbiEventFilter.shouldSkip(any(Host.class), any(OffsetDateTime.class))).thenReturn(false);
    when(factNormalizer.normalize(any(Host.class))).thenReturn(normalizedFacts);
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(swatchEventBuilder.build(anyString(), any(NormalizedFacts.class), any(Host.class), any()))
        .thenReturn(mock(Event.class));

    when(hbiHostManager.findUnmappedGuests(anyString(), anyString())).thenReturn(List.of());

    List<Event> result = eventHandler.handle(eventDTO);

    assertEquals(1, result.size());
    verify(hbiEventFilter, times(1)).shouldSkip(any(Host.class), any(OffsetDateTime.class));
    verify(factNormalizer, times(1)).normalize(any(Host.class));
    verify(hbiHostManager, times(1)).processHost(any(NormalizedFacts.class), anyString());
    verify(swatchEventBuilder, times(1)).build(anyString(), any(), any(), any());
  }

  @Test
  public void testHandleWithNormalizationAsGuestWithoutHypervisor() throws JsonProcessingException {
    FactNormalizer factNormalizer = mock(FactNormalizer.class);
    HbiHostManager hbiHostManager = mock(HbiHostManager.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    HostEventFilter hbiEventFilter = mock(HostEventFilter.class);
    SwatchEventBuilder swatchEventBuilder = mock(SwatchEventBuilder.class);
    HbiHostEventHandler eventHandler =
        new HbiHostEventHandler(
            factNormalizer, hbiHostManager, objectMapper, hbiEventFilter, swatchEventBuilder);

    HbiHostCreateUpdateEventDTO eventDTO = new HbiHostCreateUpdateEventDTO();
    HbiHost host = new HbiHost();
    host.setStaleTimestamp(OffsetDateTime.now().toString());
    eventDTO.setHost(host);

    NormalizedFacts normalizedFacts = mock(NormalizedFacts.class);
    when(normalizedFacts.isGuest()).thenReturn(true);
    when(normalizedFacts.isUnmappedGuest()).thenReturn(false);
    when(hbiEventFilter.shouldSkip(any(Host.class), any(OffsetDateTime.class))).thenReturn(false);
    when(factNormalizer.normalize(any(Host.class))).thenReturn(normalizedFacts);
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(swatchEventBuilder.build(anyString(), any(NormalizedFacts.class), any(Host.class), any()))
        .thenReturn(mock(Event.class));

    when(hbiHostManager.findHypervisorForGuest(anyString(), anyString()))
        .thenReturn(Optional.empty());

    List<Event> result = eventHandler.handle(eventDTO);

    assertEquals(1, result.size());
    verify(hbiEventFilter, times(1)).shouldSkip(any(Host.class), any(OffsetDateTime.class));
    verify(factNormalizer, times(1)).normalize(any(Host.class));
    verify(hbiHostManager, times(1)).processHost(any(NormalizedFacts.class), anyString());
    verify(swatchEventBuilder, times(1)).build(anyString(), any(), any(), any());
  }

  @Test
  public void testHandleWithFilteredEvent() {
    FactNormalizer factNormalizer = mock(FactNormalizer.class);
    HbiHostManager hbiHostManager = mock(HbiHostManager.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    HostEventFilter hbiEventFilter = mock(HostEventFilter.class);
    SwatchEventBuilder swatchEventBuilder = mock(SwatchEventBuilder.class);
    HbiHostEventHandler eventHandler =
        new HbiHostEventHandler(
            factNormalizer, hbiHostManager, objectMapper, hbiEventFilter, swatchEventBuilder);

    HbiHostCreateUpdateEventDTO eventDTO = new HbiHostCreateUpdateEventDTO();
    eventDTO.setHost(new HbiHost());
    eventDTO.getHost().setStaleTimestamp(OffsetDateTime.now().toString());

    when(hbiEventFilter.shouldSkip(any(Host.class), any(OffsetDateTime.class))).thenReturn(true);

    List<Event> result = eventHandler.handle(eventDTO);

    assertTrue(result.isEmpty());
    verify(hbiEventFilter, times(1)).shouldSkip(any(Host.class), any(OffsetDateTime.class));
    verifyNoMoreInteractions(factNormalizer, hbiHostManager, objectMapper, swatchEventBuilder);
  }

  @Test
  public void testHandleWithSuccessfulTranslation() throws JsonProcessingException {
    FactNormalizer factNormalizer = mock(FactNormalizer.class);
    HbiHostManager hbiHostManager = mock(HbiHostManager.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    HostEventFilter hbiEventFilter = mock(HostEventFilter.class);
    SwatchEventBuilder swatchEventBuilder = mock(SwatchEventBuilder.class);
    HbiHostEventHandler eventHandler =
        new HbiHostEventHandler(
            factNormalizer, hbiHostManager, objectMapper, hbiEventFilter, swatchEventBuilder);

    HbiHostCreateUpdateEventDTO eventDTO = new HbiHostCreateUpdateEventDTO();
    HbiHost host = new HbiHost();
    host.setStaleTimestamp(OffsetDateTime.now().toString());
    eventDTO.setHost(host);

    NormalizedFacts normalizedFacts = mock(NormalizedFacts.class);
    when(normalizedFacts.isHypervisor()).thenReturn(false);
    when(normalizedFacts.isGuest()).thenReturn(false);

    when(hbiEventFilter.shouldSkip(any(Host.class), any(OffsetDateTime.class))).thenReturn(false);
    when(factNormalizer.normalize(any(Host.class))).thenReturn(normalizedFacts);
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    when(swatchEventBuilder.build(
            anyString(), any(NormalizedFacts.class), any(Host.class), any(OffsetDateTime.class)))
        .thenReturn(mock(Event.class));

    List<Event> result = eventHandler.handle(eventDTO);

    assertEquals(1, result.size());
    verify(hbiEventFilter, times(1)).shouldSkip(any(Host.class), any(OffsetDateTime.class));
    verify(factNormalizer, times(1)).normalize(any(Host.class));
    verify(hbiHostManager, times(1)).processHost(any(NormalizedFacts.class), anyString());
    verify(swatchEventBuilder, times(1))
        .build(anyString(), any(NormalizedFacts.class), any(Host.class), any(OffsetDateTime.class));
  }

  @Test
  public void testHandleWithUnrecoverableException() throws JsonProcessingException {
    FactNormalizer factNormalizer = mock(FactNormalizer.class);
    HbiHostManager hbiHostManager = mock(HbiHostManager.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    HostEventFilter hbiEventFilter = mock(HostEventFilter.class);
    SwatchEventBuilder swatchEventBuilder = mock(SwatchEventBuilder.class);
    HbiHostEventHandler eventHandler =
        new HbiHostEventHandler(
            factNormalizer, hbiHostManager, objectMapper, hbiEventFilter, swatchEventBuilder);

    HbiHostCreateUpdateEventDTO eventDTO = new HbiHostCreateUpdateEventDTO();
    HbiHost host = new HbiHost();
    host.setStaleTimestamp(OffsetDateTime.now().toString());
    eventDTO.setHost(host);

    NormalizedFacts normalizedFacts = mock(NormalizedFacts.class);
    when(normalizedFacts.isHypervisor()).thenReturn(false);
    when(normalizedFacts.isGuest()).thenReturn(false);

    when(hbiEventFilter.shouldSkip(any(Host.class), any(OffsetDateTime.class))).thenReturn(false);
    when(factNormalizer.normalize(any(Host.class))).thenReturn(normalizedFacts);
    when(objectMapper.writeValueAsString(any()))
        .thenThrow(new JsonProcessingException("Serialization error") {});

    List<Event> result = eventHandler.handle(eventDTO);

    assertTrue(result.isEmpty());
    verify(hbiEventFilter, times(1)).shouldSkip(any(Host.class), any(OffsetDateTime.class));
    verify(factNormalizer, times(1)).normalize(any(Host.class));
    verify(hbiHostManager, never()).processHost(any(NormalizedFacts.class), anyString());
    verifyNoInteractions(swatchEventBuilder);
  }
}
