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

import com.redhat.swatch.kafka.config.ReadOnlyModeIsDisabled;
import com.redhat.swatch.kafka.config.ReadOnlyProvider;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the service's state as read-only based on the `swatch.swatch-contracts.enable-read-only`
 * feature flag and the property `READ_ONLY_MODE_ENABLED`. When the service is read-only, this
 * service pauses/resumes the Kafka consumers.
 */
@ApplicationScoped
@Slf4j
public class SyncChannelsWithReadOnlyService {
  @Inject Instance<ReadOnlyProvider> readOnlyProvider;
  @Inject ChannelRegistry registry;

  private Set<String> pausableChannels = Set.of();

  void onStart(@Observes StartupEvent event) {
    if (!readOnlyProvider.isResolvable()) {
      log.debug(
          "Read-only flag is not configured. Skipping the sync channels with read-only flag.");
      return;
    }

    pausableChannels = loadPausableChannels();
    log.info("Pausable consumers are: {}", pausableChannels);
  }

  @Scheduled(
      every = "${READ_ONLY_SYNC_EVERY:10s}",
      skipExecutionIf = ReadOnlyModeIsDisabled.class,
      concurrentExecution = ConcurrentExecution.SKIP)
  public void sync() {
    boolean isReadOnly = readOnlyProvider.get().isReadOnly();

    // sync pausable channels
    for (String channelName : pausableChannels) {
      PausableChannel channel = registry.getPausable(channelName);
      if (channel != null) {
        syncPausableChannel(channelName, channel, isReadOnly);
      }
    }
  }

  private void syncPausableChannel(
      String channelName, PausableChannel channel, boolean isReadOnly) {
    if (isReadOnly && !channel.isPaused()) {
      log.info("Pausing consumer '{}'", channelName);
      channel.pause();
    } else if (!isReadOnly && channel.isPaused()) {
      log.info("Resuming consumer '{}'", channelName);
      channel.resume();
    }
  }

  private Set<String> loadPausableChannels() {
    return registry.getIncomingNames().stream()
        // if it's pausable
        .filter(c -> registry.getPausable(c) != null)
        .collect(Collectors.toSet());
  }
}
