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

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "billable_usage_remittance")
public class BillableUsageRemittanceEntity implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "uuid", nullable = false)
  private UUID uuid;

  @Column(name = "org_id", nullable = false, length = 32)
  private String orgId;

  @Column(name = "product_id", nullable = false, length = 32)
  private String productId;

  @Column(name = "metric_id", nullable = false, length = 32)
  private String metricId;

  @Column(name = "accumulation_period", nullable = false, length = 255)
  private String accumulationPeriod;

  @Column(name = "sla", nullable = false, length = 32)
  private String sla;

  @Column(name = "usage", nullable = false, length = 32)
  private String usage;

  @Column(name = "billing_provider", nullable = false, length = 32)
  private String billingProvider;

  @Column(name = "billing_account_id", nullable = false, length = 255)
  private String billingAccountId;

  @Column(name = "remittance_pending_date", nullable = false)
  private OffsetDateTime remittancePendingDate;

  @Basic
  @Column(name = "remitted_pending_value", nullable = false, precision = 0)
  private Double remittedPendingValue;

  @Column(name = "retry_after")
  private OffsetDateTime retryAfter;

  @Column(name = "tally_id")
  private UUID tallyId;

  @Column(name = "hardware_measurement_type")
  private String hardwareMeasurementType;
}
