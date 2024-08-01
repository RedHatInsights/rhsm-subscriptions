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

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redhat.swatch.contract.config.ApplicationConfiguration;
import com.redhat.swatch.contract.model.SyncResult;
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
import com.redhat.swatch.contract.product.umb.CanonicalMessage;
import com.redhat.swatch.contract.product.umb.UmbSubscription;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.service.CapacityReconciliationService;
import com.redhat.swatch.contract.service.ContractService;
import com.redhat.swatch.contract.service.EnabledOrgsProducer;
import com.redhat.swatch.contract.service.OfferingProductTagLookupService;
import com.redhat.swatch.contract.service.OfferingSyncService;
import com.redhat.swatch.contract.service.SubscriptionSyncService;
import io.quarkus.runtime.configuration.ProfileManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.common.NotImplementedYet;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class ContractsTestingResource implements DefaultApi {

  public static final String FEATURE_NOT_ENABLED_MESSAGE = "This feature is not currently enabled.";
  private static final String SUCCESS_STATUS = "Success";
  private static final String FAILURE_MESSAGE = "Failed";
  private static final XmlMapper XML_MAPPER = CanonicalMessage.createMapper();

  private final ContractService service;
  private final EnabledOrgsProducer enabledOrgsProducer;
  private final ApplicationConfiguration applicationConfiguration;
  private final CapacityReconciliationService capacityReconciliationService;
  private final OfferingSyncService offeringSyncService;
  private final OfferingProductTagLookupService offeringProductTagLookupService;
  private final SubscriptionSyncService subscriptionSyncService;

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
   * @param productTag
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
      String productTag,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp)
      throws ProcessingException {
    return service.getContracts(
        orgId, productTag, billingProvider, billingAccountId, vendorProductCode, timestamp);
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
  @RolesAllowed({"test", "support"})
  public StatusResponse syncSubscriptionsForContractsByOrg(String orgId)
      throws ProcessingException {
    return service.syncSubscriptionsForContractsByOrg(orgId);
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
    var response = new OfferingResponse();
    try {
      log.info("Capacity Reconciliation for sku {} triggered", sku);
      capacityReconciliationService.reconcileCapacityForOffering(sku);
      response.setDetail(SUCCESS_STATUS);
    } catch (Exception e) {
      log.error("Error reconciling offering", e);
      response.setDetail("Error reconciling offering.");
    }
    return response;
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public RpcResponse forceSyncSubscriptionsForOrg(String orgId) throws ProcessingException {
    var response = new RpcResponse();
    try {
      subscriptionSyncService.forceSyncSubscriptionsForOrgAsync(orgId).toCompletableFuture().get();
      response.setResult(SUCCESS_STATUS);
    } catch (InterruptedException | ExecutionException e) {
      response.setResult(FAILURE_MESSAGE);
      log.error("Error synchronizing subscriptions for org {}", orgId, e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }

    return response;
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
  public RpcResponse pruneUnlistedSubscriptions() throws ProcessingException {
    enabledOrgsProducer.sendTaskForSubscriptionsPrune();
    return new RpcResponse();
  }

  /**
   * Save subscriptions manually. Supported only in dev-mode.
   *
   * @param reconcileCapacity Invoke reconciliation logic to create capacity? (hint: offering for
   *     the SKU must be present)
   * @param body JSON array containing subscriptions to save
   */
  @Override
  @RolesAllowed({"test", "support", "service"})
  public SubscriptionResponse saveSubscriptions(Boolean reconcileCapacity, String body)
      throws ProcessingException {
    var response = new SubscriptionResponse();
    if (!ProfileManager.getLaunchMode().isDevOrTest()
        && !applicationConfiguration.isManualSubscriptionEditingEnabled()) {
      response.setDetail(FEATURE_NOT_ENABLED_MESSAGE);
      return response;
    }
    try {
      log.info("Save of new subscriptions {} triggered over internal API", body);
      subscriptionSyncService.saveSubscriptions(body, reconcileCapacity);
      response.setDetail(SUCCESS_STATUS);
    } catch (Exception e) {
      log.error("Error saving subscriptions", e);
      response.setDetail("Error saving subscriptions.");
    }
    return response;
  }

  /** Sync a UMB subscription manually. */
  @Override
  @RolesAllowed({"test", "support", "service"})
  public SubscriptionResponse syncUmbSubscription(String subscriptionXml) {
    var response = new SubscriptionResponse();
    if (!applicationConfiguration.isManualSubscriptionEditingEnabled()) {
      response.setDetail(FEATURE_NOT_ENABLED_MESSAGE);
      return response;
    }
    try {
      log.info("Sync of new UMB subscription {} triggered over internal API", subscriptionXml);
      CanonicalMessage subscriptionMessage =
          XML_MAPPER.readValue(subscriptionXml, CanonicalMessage.class);
      UmbSubscription subscription = subscriptionMessage.getPayload().getSync().getSubscription();
      subscriptionSyncService.saveUmbSubscription(subscription);
      response.setDetail(SUCCESS_STATUS);
    } catch (Exception e) {
      log.error("Error syncing UMB subscription", e);
      response.setDetail("Error syncing UMB subscription.");
    }
    return response;
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public RpcResponse syncAllSubscriptions(Boolean forceSync) throws ProcessingException {
    var response = new RpcResponse();
    if (Boolean.FALSE.equals(forceSync) && !applicationConfiguration.isSubscriptionSyncEnabled()) {
      log.info(
          "Will not sync subscriptions for all opted-in orgs even though job was scheduled because subscriptionSyncEnabled=false.");
      response.setResult(FEATURE_NOT_ENABLED_MESSAGE);
      return response;
    }
    enabledOrgsProducer.sendTaskForSubscriptionsSync();
    return response;
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public OfferingResponse syncOffering(String sku) throws ProcessingException {
    var response = new OfferingResponse();
    try {
      log.info("Sync for offering {}", sku);
      SyncResult result = offeringSyncService.syncOffering(sku);
      response.detail(String.format("%s for offeringSku=\"%s\".", result, sku));
    } catch (Exception e) {
      log.error("Error syncing offering", e.getMessage());
      if (e.getCause() instanceof ApiException) {
        ApiException apiException = (ApiException) e.getCause();
        switch (apiException.getResponse().getStatus()) {
          case 400:
            throw new BadRequestException(apiException.getMessage());
          case 403:
            throw new ForbiddenException(apiException.getMessage());
          case 404:
            throw new NotFoundException(apiException.getMessage());
          default:
            throw new InternalServerErrorException(apiException.getMessage());
        }
      }
      response.setDetail("Error syncing offering");
    }
    return response;
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public OfferingResponse syncAllOfferings() throws ProcessingException {
    var response = new OfferingResponse();
    try {
      log.info("Sync all offerings triggered");
      int numProducts = offeringSyncService.syncAllOfferings();

      response.setDetail(
          String.format("Enqueued %s numProducts offerings to be synced.", numProducts));
    } catch (RuntimeException e) {
      log.error("Error enqueueing offerings to be synced. See log for details.", e);
      response.setDetail("Error enqueueing offerings to be synced");
    }
    return response;
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public OfferingProductTags getSkuProductTags(String sku) throws ProcessingException {
    return offeringProductTagLookupService.findPersistedProductTagsBySku(sku);
  }

  @Override
  @RolesAllowed({"test", "support", "service"})
  public TerminationRequest terminateSubscription(String subscriptionId, OffsetDateTime timestamp)
      throws ProcessingException {
    throw new NotImplementedYet();
  }
}
