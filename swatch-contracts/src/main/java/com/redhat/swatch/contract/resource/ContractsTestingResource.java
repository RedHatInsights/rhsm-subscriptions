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
package com.redhat.swatch.contract.resource;

import com.redhat.swatch.contract.openapi.model.AwsUsageContext;
import com.redhat.swatch.contract.openapi.model.AzureUsageContext;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.ContractRequest;
import com.redhat.swatch.contract.openapi.model.ContractResponse;
import com.redhat.swatch.contract.openapi.model.MetricResponse;
import com.redhat.swatch.contract.openapi.model.OfferingProductTags;
import com.redhat.swatch.contract.openapi.model.OfferingResponse;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.RhmUsageContext;
import com.redhat.swatch.contract.openapi.model.RpcResponse;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.openapi.model.SubscriptionResponse;
import com.redhat.swatch.contract.openapi.model.TerminationRequest;
import com.redhat.swatch.contract.openapi.resource.ApiException;
import com.redhat.swatch.contract.openapi.resource.DefaultApi;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.service.ContractService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.common.NotImplementedYet;

@Slf4j
@ApplicationScoped
public class ContractsTestingResource implements DefaultApi {

  @Inject ContractService service;

  /**
   * Create contract record in database from provided contract dto payload
   *
   * @param request
   * @return status
   * @throws ApiException
   * @throws ProcessingException
   */
  @Override
  @Transactional
  @RolesAllowed({"test"})
  public ContractResponse createContract(ContractRequest request) throws ProcessingException {
    log.info("Creating contract");
    return service.createContract(request);
  }

  @Override
  @RolesAllowed({"test"})
  public void deleteContractByUUID(String uuid) throws ProcessingException {
    log.info("Deleting contract {}", uuid);
    service.deleteContract(uuid);
  }

  /**
   * Get a list of saved contracts based on URL query parameters
   *
   * @param orgId
   * @param productId
   * @param billingProvider
   * @param billingAccountId
   * @return List<Contract> dtos
   * @throws ApiException
   * @throws ProcessingException
   */
  @Override
  @RolesAllowed({"test", "support", "service"})
  public List<Contract> getContract(
      String orgId,
      String productId,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp)
      throws ProcessingException {
    return service.getContracts(
        orgId, productId, billingProvider, billingAccountId, vendorProductCode, timestamp);
  }

  @Override
  @Transactional
  @RolesAllowed({"test", "support"})
  public StatusResponse syncAllContracts() throws ProcessingException {
    log.info("Syncing All Contracts");
    var contracts = service.getAllContracts();
    if (contracts.isEmpty()) {
      return new StatusResponse().status("No active contract found for the orgIds");
    }
    for (ContractEntity org : contracts) {
      syncContractsByOrg(org.getOrgId(), true);
    }
    return new StatusResponse().status("All Contract are Synced");
  }

  @Override
  @RolesAllowed({"test", "support"})
  public StatusResponse syncContractsByOrg(String orgId, Boolean isPreCleanup)
      throws ProcessingException {
    return service.syncContractByOrgId(orgId, isPreCleanup);
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public StatusResponse deleteContractsByOrg(String orgId) throws ProcessingException {
    return service.deleteContractsByOrgId(orgId);
  }

  @Override
  @RolesAllowed({"test"})
  public StatusResponse createPartnerEntitlementContract(PartnerEntitlementContract contract)
      throws ProcessingException {
    return service.createPartnerContract(contract);
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public OfferingResponse forceReconcileOffering(String sku) throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public RpcResponse forceSyncSubscriptionsForOrg(String orgId) throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public AwsUsageContext getAwsUsageContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String awsAccountId)
      throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public AzureUsageContext getAzureMarketplaceContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String azureAccountId)
      throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public List<MetricResponse> getMetrics(String tag) throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public RhmUsageContext getRhmUsageContext(
      String orgId, OffsetDateTime date, String productId, String sla, String usage)
      throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public OfferingProductTags getSkuProductTags(String sku) throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public RpcResponse pruneUnlistedSubscriptions() throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public SubscriptionResponse saveSubscriptions(Boolean reconcileCapacity, String body)
      throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public OfferingResponse syncAllOfferings() throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public RpcResponse syncAllSubscriptions(Boolean forceSync) throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public OfferingResponse syncOffering(String sku) throws ProcessingException {
    throw new NotImplementedYet();
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public TerminationRequest terminateSubscription(String subscriptionId, OffsetDateTime timestamp)
      throws ProcessingException {
    throw new NotImplementedYet();
  }
}
