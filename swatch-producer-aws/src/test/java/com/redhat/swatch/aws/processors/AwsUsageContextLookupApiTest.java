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
package com.redhat.swatch.aws.processors;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.redhat.swatch.aws.openapi.model.BillableUsage.BillingProviderEnum;
import com.redhat.swatch.aws.test.resources.WireMockResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = WireMockResource.class)
class AwsUsageContextLookupApiTest {
  @Inject BillableUsageAggregateProcessor processor;

  @Test
  void testParameterOrderGetAwsUsageContext() {
    var now = OffsetDateTime.now();
    var aggregate = new BillableUsageAggregate();
    aggregate.setWindowTimestamp(now);
    var key =
        new BillableUsageAggregateKey(
            "orgId",
            "productId",
            null,
            "Premium",
            "Production",
            BillingProviderEnum.AWS.value(),
            "billingAccountId");
    aggregate.setAggregateKey(key);
    try {
      processor.lookupAwsUsageContext(aggregate);
    } catch (Exception e) {
      // intentionally ignoring the exception
    }
    verify(
        getRequestedFor(urlMatching(".*/awsUsageContext.*"))
            .withQueryParam("date", equalTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now)))
            .withQueryParam("productId", equalTo("productId"))
            .withQueryParam("sla", equalTo("Premium"))
            .withQueryParam("usage", equalTo("Production"))
            .withQueryParam("awsAccountId", equalTo("billingAccountId")));
  }
}
