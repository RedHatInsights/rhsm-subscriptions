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

import com.redhat.swatch.clients.contracts.api.model.AwsUsageContext;
import com.redhat.swatch.clients.contracts.api.model.AzureUsageContext;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.ContractRequest;
import com.redhat.swatch.clients.contracts.api.model.ContractResponse;
import com.redhat.swatch.clients.contracts.api.model.MetricResponse;
import com.redhat.swatch.clients.contracts.api.model.OfferingProductTags;
import com.redhat.swatch.clients.contracts.api.model.OfferingResponse;
import com.redhat.swatch.clients.contracts.api.model.PartnerEntitlementContract;
import com.redhat.swatch.clients.contracts.api.model.RhmUsageContext;
import com.redhat.swatch.clients.contracts.api.model.RpcResponse;
import com.redhat.swatch.clients.contracts.api.model.StatusResponse;
import com.redhat.swatch.clients.contracts.api.model.SubscriptionResponse;
import com.redhat.swatch.clients.contracts.api.model.TerminationRequest;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.util.List;

public class DefaultContractsApi implements ContractsApi {

  private final DefaultApi proxied;

  public DefaultContractsApi(DefaultApi proxied) {
    this.proxied = proxied;
  }

  @Override
  public ContractResponse createContract(ContractRequest contractRequest)
      throws ProcessingException, ApiException {
    return proxied.createContract(contractRequest);
  }

  @Override
  public StatusResponse createPartnerEntitlementContract(
      PartnerEntitlementContract partnerEntitlementContract)
      throws ApiException, ProcessingException {
    return proxied.createPartnerEntitlementContract(partnerEntitlementContract);
  }

  @Override
  public void deleteContractByUUID(String uuid) throws ApiException, ProcessingException {
    proxied.deleteContractByUUID(uuid);
  }

  @Override
  public StatusResponse deleteContractsByOrg(String orgId)
      throws ApiException, ProcessingException {
    return proxied.deleteContractsByOrg(orgId);
  }

  @Override
  public OfferingResponse forceReconcileOffering(String sku)
      throws ApiException, ProcessingException {
    return proxied.forceReconcileOffering(sku);
  }

  @Override
  public RpcResponse forceSyncSubscriptionsForOrg(String orgId)
      throws ApiException, ProcessingException {
    return proxied.forceSyncSubscriptionsForOrg(orgId);
  }

  @Override
  public AwsUsageContext getAwsUsageContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String awsAccountId)
      throws ApiException, ProcessingException {
    return proxied.getAwsUsageContext(date, productId, orgId, sla, usage, awsAccountId);
  }

  @Override
  public AzureUsageContext getAzureMarketplaceContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String azureAccountId)
      throws ApiException, ProcessingException {
    return proxied.getAzureMarketplaceContext(date, productId, orgId, sla, usage, azureAccountId);
  }

  @Override
  public List<Contract> getContract(
      String orgId,
      String productTag,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp)
      throws ApiException, ProcessingException {
    return proxied.getContract(
        orgId, productTag, vendorProductCode, billingProvider, billingAccountId, timestamp);
  }

  @Override
  public List<MetricResponse> getMetrics(String tag) throws ApiException, ProcessingException {
    return proxied.getMetrics(tag);
  }

  @Override
  public RhmUsageContext getRhmUsageContext(
      String orgId, OffsetDateTime date, String productId, String sla, String usage)
      throws ApiException, ProcessingException {
    return proxied.getRhmUsageContext(orgId, date, productId, sla, usage);
  }

  @Override
  public OfferingProductTags getSkuProductTags(String sku)
      throws ApiException, ProcessingException {
    return proxied.getSkuProductTags(sku);
  }

  @Override
  public SubscriptionResponse saveSubscriptions(Boolean reconcileCapacity, String body)
      throws ApiException, ProcessingException {
    return proxied.saveSubscriptions(reconcileCapacity, body);
  }

  @Override
  public StatusResponse syncAllContracts() throws ApiException, ProcessingException {
    return proxied.syncAllContracts();
  }

  @Override
  public OfferingResponse syncAllOfferings() throws ApiException, ProcessingException {
    return proxied.syncAllOfferings();
  }

  @Override
  public RpcResponse syncAllSubscriptions(Boolean forceSync)
      throws ApiException, ProcessingException {
    return proxied.syncAllSubscriptions(forceSync);
  }

  @Override
  public StatusResponse syncContractsByOrg(
      String orgId, Boolean isPreCleanup, Boolean deleteContractsAndSubs)
      throws ApiException, ProcessingException {
    return proxied.syncContractsByOrg(orgId, isPreCleanup, deleteContractsAndSubs);
  }

  @Override
  public OfferingResponse syncOffering(String sku) throws ApiException, ProcessingException {
    return proxied.syncOffering(sku);
  }

  @Override
  public StatusResponse syncSubscriptionsForContractsByOrg(String orgId)
      throws ApiException, ProcessingException {
    return proxied.syncSubscriptionsForContractsByOrg(orgId);
  }

  @Override
  public SubscriptionResponse syncUmbSubscription(String body)
      throws ApiException, ProcessingException {
    return proxied.syncUmbSubscription(body);
  }

  @Override
  public TerminationRequest terminateSubscription(String subscriptionId, OffsetDateTime timestamp)
      throws ApiException, ProcessingException {
    return proxied.terminateSubscription(subscriptionId, timestamp);
  }
}
