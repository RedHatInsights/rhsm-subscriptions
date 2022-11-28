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

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;

/** The business key for a TallySnapshot. */
@Data
@AllArgsConstructor
public class TallySnapshotNaturalKey {

  private String orgId;
  private String swatchProductId;
  private Granularity granularity;
  private ServiceLevel serviceLevel;
  private Usage usage;
  private BillingProvider billingProvider;
  private String billingAccountId;
  private OffsetDateTime referenceDate;

  public TallySnapshotNaturalKey(TallySnapshot snapshot) {
    this.orgId = snapshot.getOrgId();
    this.swatchProductId = snapshot.getProductId();
    this.granularity = snapshot.getGranularity();
    this.serviceLevel = snapshot.getServiceLevel();
    this.usage = snapshot.getUsage();
    this.billingProvider = snapshot.getBillingProvider();
    this.referenceDate = snapshot.getSnapshotDate();
    this.billingAccountId = snapshot.getBillingAccountId();
  }
}
