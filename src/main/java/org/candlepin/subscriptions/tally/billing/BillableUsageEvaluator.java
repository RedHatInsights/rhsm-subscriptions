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
