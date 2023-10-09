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
package org.candlepin.subscriptions.test;

import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EmbeddedKafka(
    partitions = 1,
    topics = {
      "${rhsm-subscriptions.tasks.topic}",
      "${rhsm-subscriptions.subscription.tasks.topic}",
      "${rhsm-subscriptions.billing-producer.incoming.topic}",
      "${rhsm-subscriptions.billing-producer.outgoing.topic}",
      "${rhsm-subscriptions.service-instance-ingress.incoming.topic}"
    })
public interface ExtendWithEmbeddedKafka {
  @DynamicPropertySource
  static void registerKafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", () -> "${spring.embedded.kafka.brokers}");
    // In tests, messages may be sent before the listener has been assigned the topic
    // so we ensure that when the listener comes online it starts from first message.
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
  }
}
