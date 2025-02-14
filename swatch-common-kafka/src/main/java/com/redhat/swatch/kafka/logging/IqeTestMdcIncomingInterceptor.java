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
package com.redhat.swatch.kafka.logging;

import com.redhat.swatch.configuration.util.Constants;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.kafka.client.runtime.devui.model.response.KafkaMessage;
import io.smallrye.reactive.messaging.IncomingInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logmanager.MDC;

@Default
@UnlessBuildProfile("prod")
@ApplicationScoped
public class IqeTestMdcIncomingInterceptor implements IncomingInterceptor {

  @Override
  public Message<?> afterMessageReceive(Message<?> message) {
    if (message instanceof KafkaMessage kafkaMessage) {
      MDC.put(Constants.IQE_TEST_HEADER, kafkaMessage.getHeaders().get(Constants.IQE_TEST_HEADER));
    }

    return message;
  }

  @Override
  public void onMessageAck(Message<?> message) {}

  @Override
  public void onMessageNack(Message<?> message, Throwable failure) {}
}
