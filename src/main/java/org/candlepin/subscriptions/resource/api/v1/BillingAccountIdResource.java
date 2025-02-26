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
package org.candlepin.subscriptions.resource.api.v1;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.security.InsightsUserPrincipal;
import org.candlepin.subscriptions.security.auth.ReportingAccessOrInternalRequired;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingAccountIdInfo;
import org.candlepin.subscriptions.utilization.api.v1.model.BillingAccountIdResponse;
import org.candlepin.subscriptions.utilization.api.v1.resources.BillingAccountIdApi;
import org.springframework.stereotype.Component;

/** This resource is for exposing REST endpoints for Billing Account Ids. */
@Component
@Slf4j
public class BillingAccountIdResource implements BillingAccountIdApi {

  private final HostTallyBucketRepository hostTallyBucketRepository;

  @SuppressWarnings("java:S107")
  public BillingAccountIdResource(HostTallyBucketRepository hostTallyBucketRepository) {
    this.hostTallyBucketRepository = hostTallyBucketRepository;
  }

  @Transactional
  @ReportingAccessOrInternalRequired
  @Override
  public BillingAccountIdResponse fetchBillingAccountIdsForOrg(
      String orgId, String productTag, String billingProvider) {
    Object principal = ResourceUtils.getPrincipal();
    if (principal instanceof InsightsUserPrincipal userPrincipal
        && !userPrincipal.getOrgId().equals(orgId)) {
      throw new ForbiddenException("The user is not authorized to access this organization.");
    }

    List<BillingAccountIdInfo> billingAccountIds = new ArrayList<>();
    hostTallyBucketRepository
        .billingAccountIds(
            DbReportCriteria.builder()
                .orgId(orgId)
                .productTag(productTag)
                .billingProvider(BillingProvider.fromString(billingProvider))
                .build())
        .forEach(
            x ->
                billingAccountIds.add(
                    new BillingAccountIdInfo()
                        .orgId(orgId)
                        .productTag(x.productId())
                        .billingProvider(x.billingProvider().getValue())
                        .billingAccountId(x.billingAccountId())));
    return new BillingAccountIdResponse().ids(billingAccountIds);
  }
}
