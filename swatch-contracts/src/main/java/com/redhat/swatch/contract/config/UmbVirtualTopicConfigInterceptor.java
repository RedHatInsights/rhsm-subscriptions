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
package com.redhat.swatch.contract.config;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import jakarta.annotation.Priority;
import java.util.Objects;

/** Smallrye Config Interceptor that automatically prefixes UMB VirtualTopics as needed */
@Priority(Priorities.APPLICATION)
public class UmbVirtualTopicConfigInterceptor implements ConfigSourceInterceptor {
  @Override
  public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
    if (name.startsWith("mp.messaging.incoming.")
        && name.endsWith(".address")
        && Objects.nonNull(context.proceed("UMB_SERVICE_ACCOUNT_NAME"))
        && Objects.nonNull(context.proceed("UMB_NAMESPACE"))) {

      var configValue = context.proceed(name);
      var serviceAccountName = context.proceed("UMB_SERVICE_ACCOUNT_NAME").getValue();
      var namespace = context.proceed("UMB_NAMESPACE").getValue();
      return configValue.withValue(
          getConsumerTopic(namespace, serviceAccountName, configValue.getValue()));

    } else {
      return context.proceed(name);
    }
  }

  public String getConsumerTopic(String namespace, String serviceAccountName, String topic) {
    if (!topic.startsWith("VirtualTopic")) {
      return topic;
    }
    if (namespace.isEmpty() || serviceAccountName.isEmpty()) {
      throw new IllegalStateException(
          "VirtualTopic usage requires both UMB_NAMESPACE and UMB_SERVICE_ACCOUNT_NAME to be set.");
    }
    String subscriptionId = String.format("swatch-%s-%s", namespace, topic.replace(".", "_"));
    return String.format("Consumer.%s.%s.%s", serviceAccountName, subscriptionId, topic);
  }
}
