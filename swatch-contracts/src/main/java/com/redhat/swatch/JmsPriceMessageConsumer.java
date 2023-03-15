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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class JmsPriceMessageConsumer implements Runnable {
  // @Incoming("pricein")

  @Inject ConnectionFactory connectionFactory;

  private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

  private volatile String lastPrice;

  public String getLastPrice() {
    return lastPrice;
  }

  void onStart(@Observes StartupEvent ev) {
    scheduler.submit(this);
  }

  void onStop(@Observes ShutdownEvent ev) {
    scheduler.shutdown();
  }

  /*@Override
  public void run() {
      try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
          JMSConsumer consumer = context.createConsumer(context.createQueue("prices"));
          while (true) {
              Message message = consumer.receive();
              String me = consume(message.getBody(String.class));
              //throw new Exception();
              */
  /*if (message == null) return;
  lastPrice = message.getBody(String.class);*/
  /*
          }
      } catch (JMSException e) {
          throw new RuntimeException(e);
      }
  }*/

  @Override
  public void run() {
    log.info("Running consumer");
    try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
      log.info("Creating consumer");
      JMSConsumer consumer = context.createConsumer(context.createQueue("prices"));
      log.info("Entering loop");
      while (true) {
        log.info("Receiving");
        Message message = consumer.receive();
        log.info("{}", message);
        String me = consumeContract(message.getBody(String.class));
        log.info(me);
        // throw new Exception();
        /*if (message == null) return;
        lastPrice = message.getBody(String.class);*/
      }
    } catch (JMSException e) {
      throw new RuntimeException(e);
    }
  }

  public String consumeContract(String price) {
    // process your price.

    // Acknowledge the incoming message
    log.info("yaay" + price);
    return "yaay";
  }

  public String consume(String price) {
    // process your price.

    // Acknowledge the incoming message
    log.info("yaay" + price);
    return "yaay";
  }
}
