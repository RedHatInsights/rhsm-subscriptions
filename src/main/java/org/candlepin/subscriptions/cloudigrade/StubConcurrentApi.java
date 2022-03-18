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
package org.candlepin.subscriptions.cloudigrade;

import java.time.LocalDate;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReportLinks;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrentUsage;
import org.candlepin.subscriptions.cloudigrade.api.model.UsageCount;
import org.candlepin.subscriptions.cloudigrade.api.resources.ConcurrentApi;

/** Stub of the ConcurrentApi that doesn't make requests, for the methods used by subscriptions. */
public class StubConcurrentApi extends ConcurrentApi {

  @Override
  public ConcurrencyReport listDailyConcurrentUsages(
      String psk,
      String accountNumber,
      Integer limit,
      Integer offset,
      LocalDate startDate,
      LocalDate endDate)
      throws ApiException {
    return new ConcurrencyReport().links(createLinks()).addDataItem(createData());
  }

  private ConcurrentUsage createData() {
    return new ConcurrentUsage().date(LocalDate.now()).addMaximumCountsItem(createUsage());
  }

  private UsageCount createUsage() {
    return new UsageCount().arch("x86_64").instancesCount(4).role("_ANY").sla("_ANY").usage("_ANY");
  }

  private ConcurrencyReportLinks createLinks() {
    return new ConcurrencyReportLinks()
        .first("/api/cloudigrade/api/cloudigrade/v2/concurrent/?limit=10&offset=0")
        .last("/api/cloudigrade/api/cloudigrade/v2/concurrent/?limit=10&offset=0");
  }
}
