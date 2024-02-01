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

import io.smallrye.common.annotation.Blocking;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Emitter service that implements back-pressure capabilities to deal with sending messages via an
 * emitter.
 *
 * @param <T> messages to be sent.
 */
@Slf4j
@Blocking
public class EmitterService<T> {

  private static final long WAIT_MILLISECONDS = 50;

  private final Emitter<T> emitter;

  public EmitterService(Emitter<T> emitter) {
    this.emitter = emitter;
  }

  /**
   * The emitter will be sending messages asynchronously until the emitter capacity is reached. If
   * the emitter capacity is exceeded, we wait until it's freed up.
   *
   * @param message message to be sent.
   */
  public void send(Message<T> message) {
    while (!emitter.hasRequests()) {
      sleep();
    }

    emitter.send(message);
  }

  private void sleep() {
    try {
      Thread.sleep(WAIT_MILLISECONDS);
    } catch (InterruptedException ex) {
      log.warn("Thread was interrupted to send messages to the Kafka topic. ", ex);
      Thread.currentThread().interrupt();
    }
  }
}
