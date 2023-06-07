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
package com.redhat.swatch.contracts.client;

import com.redhat.swatch.contracts.api.model.Contract;
import com.redhat.swatch.contracts.api.model.Metric;
import com.redhat.swatch.contracts.api.resources.DefaultApi;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Stub class implementing RhsmApi that we can use to development against if the real thing is
 * unavailable.
 */
public class StubContactsApi extends DefaultApi {

  @Override
  public List<Contract> getContract(
      String orgId,
      String productId,
      String metricId,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp)
      throws ApiException {
    if ("org999".equals(orgId)) {
      return Collections.emptyList();
    }

    return List.of(
        createContract(
            orgId, productId, metricId, vendorProductCode, billingProvider, billingAccountId, 5),
        createContract(
            orgId, productId, metricId, vendorProductCode, billingProvider, billingAccountId, 10));
  }

  private Contract createContract(
      String orgId,
      String productId,
      String metricId,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      int value) {
    return new Contract()
        .orgId(orgId)
        .productId(productId)
        .billingProvider(billingProvider)
        .startDate(OffsetDateTime.now())
        .billingAccountId(billingAccountId)
        .vendorProductCode(vendorProductCode)
        .addMetricsItem(new Metric().metricId(metricId).value(value));
  }
}
