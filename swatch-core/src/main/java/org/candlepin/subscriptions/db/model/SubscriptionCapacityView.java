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
package org.candlepin.subscriptions.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.db.model.converters.SubscriptionCapacityViewMetricConverter;
import org.springframework.data.annotation.Immutable;

@Setter
@Getter
@Entity
@Immutable
@Table(name = "subscription_capacity_view")
public class SubscriptionCapacityView implements Serializable {

  @Id
  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column(name = "subscription_number")
  private String subscriptionNumber;

  @Column(name = "start_date")
  private OffsetDateTime startDate;

  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Column(name = "sku")
  private String sku;

  @Column(name = "product_name")
  private String productName;

  @Column(name = "service_level")
  private String serviceLevel;

  @Column(name = "usage")
  private String usage;

  @Column(name = "org_id")
  private String orgId;

  @Column(name = "billing_provider")
  private BillingProvider billingProvider;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  @Column(name = "product_tag")
  private String productTag;

  @Column(name = "quantity")
  private long quantity;

  @Column(name = "metrics", insertable = false, updatable = false)
  @Convert(converter = SubscriptionCapacityViewMetricConverter.class)
  private Set<SubscriptionCapacityViewMetric> metrics = new HashSet<>();
}
