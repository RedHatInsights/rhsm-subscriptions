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
package com.redhat.swatch;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class JmsPriceProducer {

  private Random random = new Random();

  // @Outgoing("pricesout")
  public Multi<Double> generate() {
    // Build an infinite stream of random prices
    // It emits a price every second
    log.info("Hello");
    return Multi.createFrom().ticks().every(Duration.ofSeconds(1)).map(x -> random.nextDouble());
  }

  @Inject ConnectionFactory connectionFactory;

  // private final Random random = new Random();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  void onStart(@Observes StartupEvent ev) {
      scheduler.scheduleWithFixedDelay(() -> {send("foo"); log.info("done sending"); }, 0L, 10L, TimeUnit.SECONDS);
  }

  void onStop(@Observes ShutdownEvent ev) {
      scheduler.shutdown();
  }

  public void sendContract(String contract) {
    try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
      context.createProducer().send(context.createQueue("prices"), contract);
    }
  }

  public void send(String message) {
    log.info("Sending2");
    try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
      log.info("have context");
      context.createProducer().send(context.createQueue("prices"), message);
    }
    log.info("Donnnne sending");
  }
}
