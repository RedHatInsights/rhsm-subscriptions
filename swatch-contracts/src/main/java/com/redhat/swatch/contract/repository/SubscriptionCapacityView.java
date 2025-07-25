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
package com.redhat.swatch.contract.repository;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.model.SubscriptionCapacityViewMetricConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@IdClass(SubscriptionEntity.SubscriptionCompoundId.class)
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

  @Column(name = "has_unlimited_usage")
  private Boolean hasUnlimitedUsage;

  @Column(name = "product_name")
  private String productName;

  @Column(name = "service_level")
  private ServiceLevel serviceLevel;

  @Column(name = "usage")
  private Usage usage;

  @Column(name = "org_id")
  private String orgId;

  @Column(name = "billing_provider")
  private BillingProvider billingProvider;

  @Column(name = "billing_provider_id")
  private BillingProvider billingProviderId;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  @Column(name = "product_tag")
  private String productTag;

  @Column(name = "quantity")
  private long quantity;

  @Column(name = "metrics", insertable = false, updatable = false, columnDefinition = "jsonb")
  @Convert(converter = SubscriptionCapacityViewMetricConverter.class)
  private Set<SubscriptionCapacityViewMetric> metrics = new HashSet<>();
}
