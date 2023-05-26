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
package org.candlepin.subscriptions.umb;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.candlepin.subscriptions.util.TlsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "umb")
public class UmbProperties extends TlsProperties {
  /** Message processing enabled. */
  private boolean processingEnabled = true;

  /** The service account name (usually the CN from x509 cert). */
  private String serviceAccountName;

  /** The kubernetes namespace used to create a UMB connection. */
  private String namespace;

  private String subscriptionTopic = "VirtualTopic.canonical.subscription";

  private String productTopic = "VirtualTopic.canonical.operationalProduct";

  /**
   * Factory method that produces queue names that match UMB convention for VirtualTopics when
   * needed.
   *
   * <p>UMB convention is Consumer.$service_account_name.$subscription_id.$topic
   *
   * <p>`subscription_id` is application-defined, but needs to be unique across all queue
   * subscriptions. Given subscription watch needs a single notification per *environment*, we use
   * swatch-$namespace-$topic.
   */
  private String getConsumerTopic(String topic) {
    if (!topic.startsWith("VirtualTopic")) {
      return topic;
    }
    String subscriptionId = String.format("swatch-%s-%s", namespace, topic.replace(".", "_"));
    return String.format("Consumer.%s.%s.%s", serviceAccountName, subscriptionId, topic);
  }

  public String getSubscriptionTopic() {
    return getConsumerTopic(subscriptionTopic);
  }

  public String getProductTopic() {
    return getConsumerTopic(productTopic);
  }
}
