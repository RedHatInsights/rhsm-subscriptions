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

import com.redhat.swatch.contract.openapi.model.BillingAccount;
import com.redhat.swatch.contract.openapi.model.BillingAccountIdResponse;
import com.redhat.swatch.contract.openapi.resource.ContractsV1Api;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.security.RhIdentityPrincipal;
import io.quarkus.security.ForbiddenException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ContractsV1Resource implements ContractsV1Api {

  private final SubscriptionRepository subscriptionRepository;

  @Context SecurityContext securityContext;

  public ContractsV1Resource(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @Override
  @RolesAllowed({"customer", "support"})
  public BillingAccountIdResponse fetchBillingAccountIdsForOrg(String orgId, String productTag)
      throws ProcessingException {
    validateOrgIdAccess(orgId);
    var billingAccounts =
        subscriptionRepository.findBillingAccountInfo(orgId, Optional.ofNullable(productTag));
    var ids =
        billingAccounts.stream()
            .map(
                dto ->
                    new BillingAccount()
                        .orgId(dto.orgId())
                        .billingAccountId(dto.billingAccountId())
                        .billingProvider(dto.billingProvider().getValue())
                        .productTag(dto.productTag()))
            .toList();
    return new BillingAccountIdResponse().ids(ids);
  }

  /**
   * Checks if user is allowed access to the requested orgId. Only internal users can access org ids
   * other than their own.
   * @param orgId Org that is being acted on
   * @throws ForbiddenException If user is not allowed to access org
   */
  private void validateOrgIdAccess(String orgId) throws ForbiddenException {
    Principal principal = securityContext.getUserPrincipal();
    if (principal instanceof RhIdentityPrincipal userPrincipal
        && !userPrincipal.isAssociate()
        && !userPrincipal.getIdentity().getOrgId().equals(orgId)) {
      throw new ForbiddenException("The user is not authorized to access this organization.");
    }
  }
}
