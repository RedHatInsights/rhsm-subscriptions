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

import java.io.Serializable;
import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Id;
import lombok.Data;

@Data
public class BillableUsageRemittanceEntityPK implements Serializable {

  @Column(name = "account_number", nullable = false, length = 32)
  @Id
  private String accountNumber;

  @Column(name = "product_id", nullable = false, length = 32)
  @Id
  private String productId;

  @Column(name = "metric_id", nullable = false, length = 32)
  @Id
  private String metricId;

  @Column(name = "month", nullable = false, length = 255)
  @Id
  private String month;

  @Column(name = "granularity", nullable = false, length = 32)
  @Id
  private String granularity;

  @Column(name = "snapshot_date", nullable = false)
  @Id
  private OffsetDateTime snapshotDate;

  @Column(name = "sla", nullable = false, length = 32)
  @Id
  private String sla;

  @Column(name = "usage", nullable = false, length = 32)
  @Id
  private String usage;

  @Column(name = "billing_provider", nullable = false, length = 32)
  @Id
  private String billingProvider;

  @Column(name = "billing_account_id", nullable = false, length = 255)
  @Id
  private String billingAccountId;
}
