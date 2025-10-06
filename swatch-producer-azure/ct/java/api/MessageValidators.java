package api;

import com.redhat.swatch.component.tests.api.MessageValidator;
import org.candlepin.subscriptions.billable.usage.BillableUsage.Status;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;

public class MessageValidators {

  public static MessageValidator<BillableUsageAggregate> aggregateMatches(String billingAccountId,
      Status status) {
    return new MessageValidator<>(
        aggregate ->
            billingAccountId.equals(aggregate.getAggregateKey().getBillingAccountId()) &&
                status.equals(aggregate.getStatus()),
        BillableUsageAggregate.class
    );
  }

  public static MessageValidator<BillableUsageAggregate> alwaysMatch() {
      return new MessageValidator<>(agg -> true, BillableUsageAggregate.class
    );
  }
}
