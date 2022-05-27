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
package org.candlepin.subscriptions.tally.billing;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySnapshot;
import org.candlepin.subscriptions.json.TallySnapshot.BillingProvider;
import org.candlepin.subscriptions.json.TallySummary;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BillableUsageEvaluator {

  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;

  public BillableUsageEvaluator(
      BillableUsageRemittanceRepository billableUsageRemittanceRepository) {
    this.billableUsageRemittanceRepository = billableUsageRemittanceRepository;
  }

  public BillableUsage transformMeToBillableUsage(TallySummary tallySummary) {

    return new BillableUsage()
        .withAccountNumber(tallySummary.getAccountNumber())
        .withBillableTallySnapshots(tallySummary.getTallySnapshots());
  }

  List<BillableUsageRemittanceEntity> expandIntoBillingUsageRemittanceEntities(
      TallySummary tallySummary) {

    var unsavedBillableUsageRemittanceEntities = new ArrayList<BillableUsageRemittanceEntity>();

    for (TallySnapshot tallySnapshot : tallySummary.getTallySnapshots()) {

      for (TallyMeasurement tallyMeasurement : tallySnapshot.getTallyMeasurements()) {

        var key =
            BillableUsageRemittanceEntityPK.builder()
                .usage(tallySnapshot.getUsage().value())
                .accountNumber(tallySummary.getAccountNumber())
                .billingProvider(tallySnapshot.getBillingProvider().value())
                .billingAccountId(tallySnapshot.getBillingAccountId())
                .granularity(tallySnapshot.getGranularity().value())
                .productId(tallySnapshot.getProductId())
                .sla(tallySnapshot.getSla().value())
                .snapshotDate(tallySnapshot.getSnapshotDate())
                .metricId(tallyMeasurement.getUom().value())
                .month(determineMonth())
                .build();

        BillableUsageRemittanceEntity record =
            BillableUsageRemittanceEntity.builder()
                .key(key)
                .remittanceDate(OffsetDateTime.now())
                .remittedValue(
                    decideWhatValueToSend(tallySnapshot.getBillingProvider(), tallyMeasurement))
                .build();

        unsavedBillableUsageRemittanceEntities.add(record);
      }
    }

    return unsavedBillableUsageRemittanceEntities;
  }

  // TODO
  private String determineMonth() {

    return "2022-05";
  }

  private Double decideWhatValueToSend(
      BillingProvider billingProvider, TallyMeasurement tallyMeasurement) {

    switch (billingProvider) {
      case AWS:
        return performSomeMagic(tallyMeasurement);
      default:
        break;
    }

    return tallyMeasurement.getValue();
  }

  /**
   * @param tallyMeasurement
   * @return Double
   */
  private Double performSomeMagic(TallyMeasurement tallyMeasurement) {
    /* for AWS this means:

    - get the value of the measurement
    - look up instance monthly value - best way to do this?  do we want to query the instance_monthly_totals table now,
    or maybe we want to update the tallySummary event we pull from the topic to include it?
    - if(tallyMeasurement.getValue() - instance_monthly_value) >= 1, return ceil(tallyMeasurement.getValue())

    */
    return tallyMeasurement.getValue();
  }

  public BillableUsageRemittanceEntity saveATrackingTableThing(
      BillableUsageRemittanceEntity billableUsage) {

    return billableUsageRemittanceRepository.save(billableUsage);
  }
}
