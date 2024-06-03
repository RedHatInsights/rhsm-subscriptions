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
import com.redhat.swatch.clients.contracts.api.model.Metric;
import com.redhat.swatch.clients.contracts.api.model.MetricResponse;
import com.redhat.swatch.clients.contracts.api.model.OfferingProductTags;
import com.redhat.swatch.clients.contracts.api.model.OfferingResponse;
import com.redhat.swatch.clients.contracts.api.model.PartnerEntitlementContract;
import com.redhat.swatch.clients.contracts.api.model.RhmUsageContext;
import com.redhat.swatch.clients.contracts.api.model.RpcResponse;
import com.redhat.swatch.clients.contracts.api.model.StatusResponse;
import com.redhat.swatch.clients.contracts.api.model.SubscriptionResponse;
import com.redhat.swatch.clients.contracts.api.model.TerminationRequest;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/**
 * Stub class implementing Contracts API that we can use to development against if the real thing is
 * unavailable.
 */
public class StubContractsApi implements DefaultApi {

  private static final String CONTRACT_METRIC_ID = "four_vcpu_0";

  @Override
  public List<Contract> getContract(
      String orgId,
      String productTag,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp) {
    if ("org999".equals(orgId)) {
      return Collections.emptyList();
    }

    return List.of(
        createContract(
            orgId,
            productTag,
            CONTRACT_METRIC_ID,
            vendorProductCode,
            billingProvider,
            billingAccountId,
            5),
        createContract(
            orgId,
            productTag,
            CONTRACT_METRIC_ID,
            vendorProductCode,
            billingProvider,
            billingAccountId,
            10));
  }

  @Override
  public ContractResponse createContract(ContractRequest contractRequest)
      throws ProcessingException {
    return null;
  }

  @Override
  public StatusResponse createPartnerEntitlementContract(
      PartnerEntitlementContract partnerEntitlementContract) throws ProcessingException {
    return null;
  }

  @Override
  public void deleteContractByUUID(String uuid) throws ProcessingException {
    // not implemented in the stub.
  }

  @Override
  public StatusResponse deleteContractsByOrg(String orgId) throws ProcessingException {
    return null;
  }

  @Override
  public OfferingResponse forceReconcileOffering(String sku) throws ProcessingException {
    return null;
  }

  @Override
  public RpcResponse forceSyncSubscriptionsForOrg(String orgId) throws ProcessingException {
    return null;
  }

  @Override
  public AwsUsageContext getAwsUsageContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String awsAccountId)
      throws ProcessingException {
    return null;
  }

  @Override
  public AzureUsageContext getAzureMarketplaceContext(
      OffsetDateTime date,
      String productId,
      String orgId,
      String sla,
      String usage,
      String azureAccountId)
      throws ProcessingException {
    return null;
  }

  @Override
  public List<MetricResponse> getMetrics(String tag) throws ProcessingException {
    return List.of();
  }

  @Override
  public RhmUsageContext getRhmUsageContext(
      String orgId, OffsetDateTime date, String productId, String sla, String usage)
      throws ProcessingException {
    return null;
  }

  @Override
  public OfferingProductTags getSkuProductTags(String sku) throws ProcessingException {
    return null;
  }

  @Override
  public RpcResponse pruneUnlistedSubscriptions() throws ProcessingException {
    return null;
  }

  @Override
  public SubscriptionResponse saveSubscriptions(Boolean reconcileCapacity, String body)
      throws ProcessingException {
    return null;
  }

  @Override
  public StatusResponse syncAllContracts() throws ProcessingException {
    return null;
  }

  @Override
  public OfferingResponse syncAllOfferings() throws ProcessingException {
    return null;
  }

  @Override
  public RpcResponse syncAllSubscriptions(Boolean forceSync) throws ProcessingException {
    return null;
  }

  @Override
  public StatusResponse syncContractsByOrg(String orgId, Boolean isPreCleanup)
      throws ProcessingException {
    return null;
  }

  @Override
  public StatusResponse syncSubscriptionsForContractsByOrg(String orgId)
      throws ProcessingException {
    return null;
  }

  @Override
  public OfferingResponse syncOffering(String sku) throws ProcessingException {
    return null;
  }

  @Override
  public TerminationRequest terminateSubscription(String subscriptionId, OffsetDateTime timestamp)
      throws ProcessingException {
    return null;
  }

  private static Contract createContract(
      String orgId,
      String productTag,
      String metricId,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      int value) {
    Contract contract =
        new Contract()
            .orgId(orgId)
            .billingProvider(billingProvider)
            .startDate(OffsetDateTime.of(2022, 1, 1, 1, 0, 0, 0, ZoneOffset.UTC))
            .billingAccountId(billingAccountId)
            .vendorProductCode(vendorProductCode)
            .addMetricsItem(new Metric().metricId(metricId).value(value));
    if (productTag != null) {
      contract.setProductTags(List.of(productTag));
    }
    return contract;
  }
}
