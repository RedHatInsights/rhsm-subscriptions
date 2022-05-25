package org.candlepin.subscriptions.rhmarketplace.billable_usage;

import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.json.TallySummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BillingUsageEvaluator {

  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;

  @Autowired
  public BillingUsageEvaluator(BillableUsageRemittanceRepository billableUsageRemittanceRepository) {
    this.billableUsageRemittanceRepository = billableUsageRemittanceRepository;
  }

  public void receive(TallySummary tallySummary) {

    System.out.println(tallySummary);
  }
}
