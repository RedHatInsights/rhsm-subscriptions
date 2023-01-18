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
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** An embeddable composite key for a tally instance view. */
@Embeddable
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TallyInstanceViewKey implements Serializable {

  @Column(name = "instance_id")
  private UUID instanceId;

  @Column(name = "product_id")
  private String productId;

  private ServiceLevel sla;

  private Usage usage;

  @Column(name = "bucket_billing_provider")
  private BillingProvider bucketBillingProvider;

  @Column(name = "bucket_billing_account_id")
  private String bucketBillingAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "measurement_type")
  private HardwareMeasurementType measurementType;
}
