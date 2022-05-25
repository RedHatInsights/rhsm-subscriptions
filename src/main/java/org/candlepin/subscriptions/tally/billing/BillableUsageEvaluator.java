package org.candlepin.subscriptions.tally.billing;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.json.BillableUsage;
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

    log.info("Evaluate Me Please {}", tallySummary);

    return new BillableUsage()
        .withAccountNumber(tallySummary.getAccountNumber())
        .withBillableTallySnapshots(tallySummary.getTallySnapshots());
  }
}
