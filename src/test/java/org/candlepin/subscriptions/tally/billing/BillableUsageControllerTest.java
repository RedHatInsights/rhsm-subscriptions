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

import java.util.List;
import lombok.Data;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.junit.jupiter.api.Test;

class BillableUsageControllerTest {

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

    List<Person> people = List.of(new Person("John", "male"), new Person("Lindsey", "female"));

    // Declarative/Functional Programming

  }

  @Data
  class Person {
    private final String name;
    private final String gender;
  }
}
