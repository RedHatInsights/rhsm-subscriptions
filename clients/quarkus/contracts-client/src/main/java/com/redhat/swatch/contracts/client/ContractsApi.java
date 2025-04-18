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
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.util.List;

public interface ContractsApi {

  ContractResponse createContract(ContractRequest contractRequest) throws ApiException;

  StatusResponse createPartnerEntitlementContract(
      PartnerEntitlementContract partnerEntitlementContract)
      throws ApiException, ProcessingException;

  void deleteContractByUUID(String uuid) throws ApiException, ProcessingException;

  StatusResponse deleteContractsByOrg(String orgId) throws ApiException, ProcessingException;

  OfferingResponse forceReconcileOffering(String sku) throws ApiException, ProcessingException;

  RpcResponse forceSyncSubscriptionsForOrg(String orgId) throws ApiException, ProcessingException;

  AwsUsageContext getAwsUsageContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String awsAccountId)
      throws ApiException, ProcessingException;

  AzureUsageContext getAzureMarketplaceContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String azureAccountId)
      throws ApiException, ProcessingException;

  List<Contract> getContract(
      String orgId,
      String productTag,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp)
      throws ApiException, ProcessingException;

  List<MetricResponse> getMetrics(String tag) throws ApiException, ProcessingException;

  RhmUsageContext getRhmUsageContext(
      String orgId, OffsetDateTime date, String productId, String sla, String usage)
      throws ApiException, ProcessingException;

  OfferingProductTags getSkuProductTags(String sku) throws ApiException, ProcessingException;

  SubscriptionResponse saveSubscriptions(Boolean reconcileCapacity, String body)
      throws ApiException, ProcessingException;

  StatusResponse syncAllContracts() throws ApiException, ProcessingException;

  OfferingResponse syncAllOfferings() throws ApiException, ProcessingException;

  RpcResponse syncAllSubscriptions(Boolean forceSync) throws ApiException, ProcessingException;

  StatusResponse syncContractsByOrg(
      String orgId, Boolean isPreCleanup, Boolean deleteContractsAndSubs)
      throws ApiException, ProcessingException;

  OfferingResponse syncOffering(String sku) throws ApiException, ProcessingException;

  StatusResponse syncSubscriptionsForContractsByOrg(String orgId)
      throws ApiException, ProcessingException;

  SubscriptionResponse syncUmbSubscription(String body) throws ApiException, ProcessingException;

  TerminationRequest terminateSubscription(String subscriptionId, OffsetDateTime timestamp)
      throws ApiException, ProcessingException;
}
