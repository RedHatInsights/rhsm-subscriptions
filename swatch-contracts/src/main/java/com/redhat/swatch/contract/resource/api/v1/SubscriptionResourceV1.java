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
package com.redhat.swatch.contract.resource.api.v1;

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.openapi.model.BillingProviderType;
import com.redhat.swatch.contract.openapi.model.ReportCategory;
import com.redhat.swatch.contract.openapi.model.ServiceLevelType;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportSortV1;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportV1;
import com.redhat.swatch.contract.openapi.model.SortDirection;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.openapi.resource.SubscriptionsV1Api;
import com.redhat.swatch.contract.resource.OrgIdResolver;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import java.time.OffsetDateTime;

/** Subscriptions Table API implementation. */
@ApplicationScoped
public class SubscriptionResourceV1 implements SubscriptionsV1Api {
  @Context SecurityContext securityContext;
  @Context HttpHeaders httpHeaders;
  @Inject SubscriptionTableControllerV1 subscriptionTableController;
  @Inject OrgIdResolver orgIdResolver;

  @RolesAllowed({"customer", "service"})
  @Override
  public SkuCapacityReportV1 getSkuCapacityReportV1(
      ProductId productId,
      @Min(0) Integer offset,
      @Min(1) Integer limit,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usage,
      BillingProviderType billingProvider,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String metricId,
      SkuCapacityReportSortV1 sort,
      SortDirection dir) {
    return subscriptionTableController.capacityReportBySkuV1(
        orgIdResolver.getOrgId(securityContext, httpHeaders),
        productId,
        offset,
        limit,
        category,
        sla,
        usage,
        billingProvider,
        billingAccountId,
        beginning,
        ending,
        metricId,
        sort,
        dir);
  }
}
