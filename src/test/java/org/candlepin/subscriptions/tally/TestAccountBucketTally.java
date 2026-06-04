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
package org.candlepin.subscriptions.tally;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.candlepin.subscriptions.db.model.AccountBucketTally;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

/**
 * Since we can't instantiate the AccountBucketTally projection interface, we implement one for
 * testing.
 */
@Data
@AllArgsConstructor
public class TestAccountBucketTally implements AccountBucketTally {
  private String productId;
  private HardwareMeasurementType measurementType;
  private ServiceLevel sla;
  private Usage usage;
  private BillingProvider billingProvider;
  private String billingAccountId;
  private Double cores;
  private Double sockets;
  private Double instances;
}
