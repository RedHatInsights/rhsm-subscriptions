package com.redhat.swatch.contract.config;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.ConfigValue.ConfigValueBuilder;
import io.smallrye.config.Priorities;
import javax.annotation.Priority;

/** Smallrye Config Interceptor that automatically prefixes UMB VirtualTopics as needed */
@Priority(Priorities.APPLICATION)
public class UmbVirtualTopicConfigInterceptor implements ConfigSourceInterceptor {
  @Override
  public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
//    if (name.startsWith("mp.messaging.incoming.") && name.endsWith(".address")) {
//      var configValue = context.proceed(name);
//      var serviceAccountName = context.proceed("UMB_SERVICE_ACCOUNT_NAME");
//      var namespace = context.proceed("UMB_NAMESPACE");
//      return configValue.withValue(getConsumerTopic(namespace, serviceAccountName, configValue));
//    }
//    else {
      return context.proceed(name);
//    }
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
