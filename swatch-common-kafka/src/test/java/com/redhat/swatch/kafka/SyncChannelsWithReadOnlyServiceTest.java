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
package com.redhat.swatch.kafka;

import com.redhat.swatch.kafka.config.ReadOnlyProvider;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import jakarta.enterprise.inject.Instance;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncChannelsWithReadOnlyServiceTest {

  private static final String PAUSABLE_CHANNEL = "contracts-from-gateway";
  private static final String NON_PAUSABLE_CHANNEL = "enabled-orgs";

  @Mock Instance<ReadOnlyProvider> readOnlyProviderInstance;
  @Mock ReadOnlyProvider readOnlyProvider;
  @Mock ChannelRegistry registry;
  @Mock PausableChannel pausableChannel;

  @InjectMocks SyncChannelsWithReadOnlyService service;

  @BeforeEach
  void setUp() {
    Mockito.when(readOnlyProviderInstance.isResolvable()).thenReturn(true);
    Mockito.when(readOnlyProviderInstance.get()).thenReturn(readOnlyProvider);
    Mockito.when(registry.getIncomingNames())
        .thenReturn(Set.of(PAUSABLE_CHANNEL, NON_PAUSABLE_CHANNEL));
    Mockito.when(registry.getPausable(PAUSABLE_CHANNEL)).thenReturn(pausableChannel);
    Mockito.when(registry.getPausable(NON_PAUSABLE_CHANNEL)).thenReturn(null);
    service.onStart(null);
  }

  @Test
  void syncPausesChannelWhenReadOnlyAndChannelIsRunning() {
    Mockito.when(readOnlyProvider.isReadOnly()).thenReturn(true);
    Mockito.when(pausableChannel.isPaused()).thenReturn(false);

    service.sync();

    Mockito.verify(pausableChannel).pause();
    Mockito.verify(pausableChannel, Mockito.never()).resume();
  }

  @Test
  void syncDoesNotPauseAgainWhenAlreadyPaused() {
    Mockito.when(readOnlyProvider.isReadOnly()).thenReturn(true);
    Mockito.when(pausableChannel.isPaused()).thenReturn(true);

    service.sync();

    Mockito.verify(pausableChannel, Mockito.never()).pause();
    Mockito.verify(pausableChannel, Mockito.never()).resume();
  }

  @Test
  void syncResumesChannelWhenNotReadOnlyAndChannelIsPaused() {
    Mockito.when(readOnlyProvider.isReadOnly()).thenReturn(false);
    Mockito.when(pausableChannel.isPaused()).thenReturn(true);

    service.sync();

    Mockito.verify(pausableChannel).resume();
    Mockito.verify(pausableChannel, Mockito.never()).pause();
  }

  @Test
  void syncDoesNotResumeAgainWhenAlreadyRunning() {
    Mockito.when(readOnlyProvider.isReadOnly()).thenReturn(false);
    Mockito.when(pausableChannel.isPaused()).thenReturn(false);

    service.sync();

    Mockito.verify(pausableChannel, Mockito.never()).pause();
    Mockito.verify(pausableChannel, Mockito.never()).resume();
  }

  @Test
  void syncIgnoresNonPausableIncomingChannels() {
    Mockito.clearInvocations(registry, pausableChannel);
    Mockito.when(readOnlyProvider.isReadOnly()).thenReturn(true);
    Mockito.when(pausableChannel.isPaused()).thenReturn(false);

    service.sync();

    Mockito.verify(registry).getPausable(PAUSABLE_CHANNEL);
    Mockito.verify(registry, Mockito.never()).getPausable(NON_PAUSABLE_CHANNEL);
    Mockito.verify(pausableChannel).pause();
  }

  @Test
  void syncSkipsChannelWhenPausableRegistrationDisappears() {
    Mockito.when(registry.getPausable(PAUSABLE_CHANNEL)).thenReturn(null);
    Mockito.when(readOnlyProvider.isReadOnly()).thenReturn(true);

    service.sync();

    Mockito.verify(pausableChannel, Mockito.never()).pause();
    Mockito.verify(pausableChannel, Mockito.never()).resume();
  }
}
