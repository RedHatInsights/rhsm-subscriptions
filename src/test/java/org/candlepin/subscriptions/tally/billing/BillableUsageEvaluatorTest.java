package org.candlepin.subscriptions.tally.billing;

import java.util.List;
import lombok.Data;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.junit.jupiter.api.Test;

public class BillableUsageEvaluatorTest {

  private static final String ACCOUNT_NUMBER = "acct123";

  BillableUsageRemittanceEntity createBillableUsageRemittanceEntity() {
    //    return BillableUsageRemittanceEntity.builder()
    //        .usage(Usage.ANY.value())
    //        .accountNumber(ACCOUNT_NUMBER)
    //        .billingProvider(BillingProvider.ANY.value())
    //        .billingAccountId(tallySnapshot.getBillingAccountId())
    //        .granularity(tallySnapshot.getGranularity().toString())
    //        .productId(tallySnapshot.getProductId())
    //        .sla(tallySnapshot.getSla().toString())
    //        .snapshotDate(tallySnapshot.getSnapshotDate())
    //        .metricId(tallyMeasurement.getUom().toString())
    //        .month("2022-05") // TODO
    //        .remittanceDate(OffsetDateTime.now())
    //        .remittedValue(tallyMeasurement.getValue())
    //        .build();
    return null;
  }

  void testBillingProviderEmpty() {}

  void testBillingProviderRedHat() {
    /*
    If the Billing Provider is Red Hat, we create BillableUsage with all the same values
    as they were sent to us in TallySummary
     */

  }

  void testBillingProviderAWS() {

    /*
    If the BillingProvider is AWS, we need to find the monthly total usage
     */

  }

  void testBillingProviderGCP() {}

  void testBillingProviderAzure() {}

  void testBillingProviderOracle() {}

  @Test
  void testBillingProvider_ANY() {

    List<Person> people = List.of(
        new Person("John", "male"),
        new Person("Lindsey", "female")
    );

    //Declarative/Functional Programming

  }

  @Data
  class Person {
    private final String name;
    private final String gender;
  }
}
