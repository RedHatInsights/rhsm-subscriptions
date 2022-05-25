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
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySnapshot;
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

  public BillableUsage evaluateMe(TallySummary tallySummary) {

    var trackingTableRecords = saveThisPuppyToTheTrackingTable(tallySummary);

    log.info("Tracking Table Records: {}", trackingTableRecords);

    return new BillableUsage()
        .withAccountNumber(tallySummary.getAccountNumber())
        .withBillableTallySnapshots(tallySummary.getTallySnapshots());
  }

  private List<BillableUsageRemittanceEntity> saveThisPuppyToTheTrackingTable(
      TallySummary tallySummary) {

    var saved = new ArrayList<BillableUsageRemittanceEntity>();

    var accountNumber = tallySummary.getAccountNumber();

    for (TallySnapshot tallySnapshot : tallySummary.getTallySnapshots()) {

      for (TallyMeasurement tallyMeasurement : tallySnapshot.getTallyMeasurements()) {
        BillableUsageRemittanceEntity record =
            BillableUsageRemittanceEntity.builder()
                .usage(tallySnapshot.getUsage().toString())
                .accountNumber(accountNumber)
                .billingProvider(tallySnapshot.getProductId())
                .billingAccountId(tallySnapshot.getBillingAccountId())
                .granularity(tallySnapshot.getGranularity().toString())
                .productId(tallySnapshot.getProductId())
                .sla(tallySnapshot.getSla().toString())
                .snapshotDate(tallySnapshot.getSnapshotDate())
                .metricId(tallyMeasurement.getUom().toString())
                .month("2022-05") // TODO
                .remittanceDate(OffsetDateTime.now())
                .remittedValue(tallyMeasurement.getValue())
                .build();

        saved.add(billableUsageRemittanceRepository.save(record));
      }
    }

    return saved;
  }
}
