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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.utils.ContractMessageProcessingResult.INVALID_MESSAGE_UNPROCESSED;
import static com.redhat.swatch.contract.utils.ContractMessageProcessingResult.NEW_CONTRACT_CREATED;
import static com.redhat.swatch.contract.utils.ContractMessageProcessingResult.PARTNER_API_FAILURE;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.PageRequest;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1.SourcePartnerEnum;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.exception.ContractNotAssociatedToOrgException;
import com.redhat.swatch.contract.exception.ContractValidationFailedException;
import com.redhat.swatch.contract.model.ContractMapper;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.ContractRequest;
import com.redhat.swatch.contract.openapi.model.ContractResponse;
import com.redhat.swatch.contract.openapi.model.OfferingProductTags;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractRepository;
import com.redhat.swatch.contract.repository.Specification;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.utils.ContractMessageProcessingResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Service layer for interfacing with database and external APIs for manipulation of swatch Contract
 * records
 */
@Slf4j
@ApplicationScoped
public class ContractService {

  public static final String SUCCESS_MESSAGE = "SUCCESS";
  public static final String FAILURE_MESSAGE = "FAILED";

  private final ContractRepository contractRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final ContractMapper mapper;
  private final MeasurementMetricIdTransformer measurementMetricIdTransformer;
  private final SubscriptionSyncService syncService;
  @Inject @RestClient PartnerApi partnerApi;
  @Inject @RestClient SearchApi subscriptionApi;
  @Inject Validator validator;
  private final List<BasePartnerEntitlementsProvider> partnerEntitlementsProviders;

  ContractService(
      ContractRepository contractRepository,
      SubscriptionRepository subscriptionRepository,
      ContractMapper mapper,
      MeasurementMetricIdTransformer measurementMetricIdTransformer,
      SubscriptionSyncService syncService,
      AwsPartnerEntitlementsProvider awsPartnerEntitlementsProvider,
      AzurePartnerEntitlementsProvider azurePartnerEntitlementsProvider) {
    this.contractRepository = contractRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.mapper = mapper;
    this.measurementMetricIdTransformer = measurementMetricIdTransformer;
    this.syncService = syncService;
    this.partnerEntitlementsProviders =
        List.of(awsPartnerEntitlementsProvider, azurePartnerEntitlementsProvider);
  }

  @Transactional
  public ContractResponse createContract(ContractRequest request) {
    ContractResponse response = new ContractResponse();
    try {
      var result =
          upsertPartnerContract(
              request.getPartnerEntitlementContract(),
              request.getPartnerEntitlement(),
              request.getSubscriptionId());
      response.setStatus(result.toStatus());
      if (result.isValid() && result.getEntity() != null) {
        response.setContract(mapper.contractEntityToDto(result.getEntity()));
      }
    } catch (ContractNotAssociatedToOrgException e) {
      response.setStatus(ContractMessageProcessingResult.RH_ORG_NOT_ASSOCIATED.toStatus());
    } catch (ContractValidationFailedException e) {
      response.setStatus(buildContractDetailsMissingStatus(e));
    }

    return response;
  }

  @Transactional
  public List<ContractEntity> getAllContracts() {
    return contractRepository.findAll().stream().toList();
  }

  /**
   * Build Specifications based on provided parameters if not null and use to query the database
   * based on specifications.
   *
   * @param orgId the org ID.
   * @param productId the product ID.
   * @param billingProvider the billing provider.
   * @param billingAccountId the billing account ID.
   * @param vendorProductCode the vendor product code.
   * @return List<Contract> the list of contracts.
   */
  public List<Contract> getContracts(
      String orgId,
      String productId,
      String billingProvider,
      String billingAccountId,
      String vendorProductCode,
      OffsetDateTime timestamp) {

    Specification<ContractEntity> specification = ContractEntity.orgIdEquals(orgId);

    if (productId != null) {
      specification = specification.and(ContractEntity.productIdEquals(productId));
    }
    if (billingProvider != null) {
      specification = specification.and(ContractEntity.billingProviderEquals(billingProvider));
    }
    if (billingAccountId != null) {
      specification = specification.and(ContractEntity.billingAccountIdEquals(billingAccountId));
    }
    if (vendorProductCode != null) {
      specification = specification.and(ContractEntity.vendorProductCodeEquals(vendorProductCode));
    }
    if (timestamp != null) {
      specification = specification.and(ContractEntity.activeOn(timestamp));
    }

    return contractRepository.getContracts(specification).stream()
        .map(mapper::contractEntityToDto)
        .toList();
  }

  /**
   * Delete a contract for a given uuid. This is soft delete. It sets the end date of a contract to
   * the current timestamp.
   *
   * @param uuid the contract id.
   */
  @Transactional
  public void deleteContract(String uuid) {
    var contract = contractRepository.findContract(UUID.fromString(uuid));
    deleteContract(contract);
  }

  @Transactional
  public StatusResponse createPartnerContract(PartnerEntitlementContract contract) {
    var partnerEntitlementsProvider = findPartnerEntitlementsProvider(contract);
    if (partnerEntitlementsProvider == null) {
      log.info("Can't process the contract from UMB: {}", contract);
      return INVALID_MESSAGE_UNPROCESSED.toStatus();
    }

    try {
      return callPartnerApiAndUpsertPartnerContract(contract, partnerEntitlementsProvider);
    } catch (ProcessingException | ApiException e) {
      log.error(e.getMessage());
      return PARTNER_API_FAILURE.toStatus();
    } catch (ContractValidationFailedException e) {
      return buildContractDetailsMissingStatus(e);
    }
  }

  @Retry(delay = 500, maxRetries = 10, abortOn = ContractNotAssociatedToOrgException.class)
  public StatusResponse callPartnerApiAndUpsertPartnerContract(
      PartnerEntitlementContract contract,
      BasePartnerEntitlementsProvider partnerEntitlementsProvider)
      throws ApiException, ContractValidationFailedException {
    try {
      var entitlement = partnerEntitlementsProvider.getPartnerEntitlement(contract);
      var subscriptionId =
          Optional.ofNullable(findSubscriptionNumber(entitlement))
              .orElse(contract.getRedHatSubscriptionNumber());
      return upsertPartnerContract(contract, entitlement, subscriptionId).toStatus();
    } catch (ContractNotAssociatedToOrgException e) {
      return ContractMessageProcessingResult.RH_ORG_NOT_ASSOCIATED.toStatus();
    }
  }

  @Transactional
  public ContractMessageProcessingResult upsertPartnerContract(
      PartnerEntitlementContract contract, PartnerEntitlementV1 entitlement, String subscriptionId)
      throws ContractNotAssociatedToOrgException, ContractValidationFailedException {
    ContractEntity entity = mapper.partnerContractToContractEntity(contract);
    return upsertPartnerContract(entity, entitlement, subscriptionId);
  }

  @Transactional
  public ContractMessageProcessingResult upsertPartnerContract(
      ContractEntity entity, PartnerEntitlementV1 entitlement, String subscriptionId)
      throws ContractNotAssociatedToOrgException, ContractValidationFailedException {
    try {
      mapUpstreamContractToContractEntity(entity, entitlement);

      if (entity.getOrgId() == null) {
        throw new ContractNotAssociatedToOrgException();
      }
      var violations = validator.validate(entity);
      if (!violations.isEmpty()) {
        throw new ContractValidationFailedException(entity, violations);
      }

    } catch (NumberFormatException e) {
      log.error(e.getMessage());
      return INVALID_MESSAGE_UNPROCESSED;
    }

    List<ContractEntity> existingContractRecords = findExistingContractRecords(entity);
    // There may be multiple "versions" of a contract, e.g. if a contract quantity changes.
    // We should make updates as necessary to each, it's possible to update both the metadata and
    // the quantity at once.
    var statuses =
        existingContractRecords.stream()
            .map(existing -> updateContractRecord(existing, entity, subscriptionId))
            .toList();
    if (!statuses.isEmpty()) {
      return combineStatuses(statuses);
    } else {
      // New contract
      if (entity.getSubscriptionNumber() != null) {
        persistSubscription(createSubscriptionForContract(entity, subscriptionId));
      }
      persistContract(entity, OffsetDateTime.now());
      return NEW_CONTRACT_CREATED.withContract(entity);
    }
  }

  private BasePartnerEntitlementsProvider findPartnerEntitlementsProvider(
      PartnerEntitlementContract contract) {
    for (BasePartnerEntitlementsProvider provider : partnerEntitlementsProviders) {
      if (provider.isFor(contract)) {
        return provider;
      }
    }

    return null;
  }

  private ContractMessageProcessingResult combineStatuses(
      List<ContractMessageProcessingResult> results) {
    // Different things happen to the contract records during processing...
    // Elevate some specific results, logging will have further details of all that happened during
    // message processing
    for (var result :
        List.of(
            ContractMessageProcessingResult.CONTRACT_SPLIT_DUE_TO_CAPACITY_UPDATE,
            ContractMessageProcessingResult.METADATA_UPDATED)) {
      if (results.contains(result)) {
        return result;
      }
    }
    // Otherwise, just return the first result
    return results.get(0);
  }

  private ContractMessageProcessingResult updateContractRecord(
      ContractEntity existingContract, ContractEntity entity, String subscriptionId) {
    if (Objects.equals(entity, existingContract)) {
      log.info(
          "Duplicate contract found that matches the record for uuid {}",
          existingContract.getUuid());
      return ContractMessageProcessingResult.REDUNDANT_MESSAGE_IGNORED.withContract(entity);
    } else if (isContractQuantityChanged(entity, existingContract)) {
      // Record found in contract table but the contract quantities or dates have changed
      if (!Objects.equals(entity.getEndDate(), existingContract.getEndDate())) {
        // Skip this particular record because it's already been terminated for a quantity change.
        return ContractMessageProcessingResult.REDUNDANT_MESSAGE_IGNORED.withContract(entity);
      }
      var now = OffsetDateTime.now();
      if (existingContract.getSubscriptionNumber() != null) {
        persistExistingSubscription(
            existingContract, now, subscriptionId); // end current subscription
        var newSubscription = createSubscriptionForContract(entity, subscriptionId);
        newSubscription.setStartDate(now);
        persistSubscription(newSubscription);
      }
      persistExistingContract(existingContract, now); // Persist previous contract
      entity.setStartDate(now);
      persistContract(entity, now); // Persist new contract
      log.info("Previous contract archived and new contract created");
      return ContractMessageProcessingResult.CONTRACT_SPLIT_DUE_TO_CAPACITY_UPDATE.withContract(
          entity);
    } else {
      // Record found in contract table but a non-quantity change occurred (e.g. an identifier was
      // updated).
      mapper.updateContract(existingContract, entity);
      persistContract(existingContract, OffsetDateTime.now());
      if (existingContract.getSubscriptionNumber() != null) {
        SubscriptionEntity subscription =
            createOrUpdateSubscription(existingContract, subscriptionId);
        subscriptionRepository.persist(subscription);
      }
      log.info("Contract metadata updated");
      return ContractMessageProcessingResult.METADATA_UPDATED.withContract(entity);
    }
  }

  private boolean isContractQuantityChanged(
      ContractEntity entity, ContractEntity existingContract) {
    return !Objects.equals(entity.getMetrics(), existingContract.getMetrics());
  }

  private void persistExistingSubscription(
      ContractEntity contract, OffsetDateTime now, String subscriptionId) {
    var subscription =
        subscriptionRepository
            .findOne(SubscriptionEntity.class, SubscriptionEntity.forContract(contract))
            .orElseGet(() -> createSubscriptionForContract(contract, subscriptionId));
    subscription.setEndDate(now);
    subscriptionRepository.persist(subscription);
  }

  private SubscriptionEntity createOrUpdateSubscription(
      ContractEntity contract, String subscriptionId) {
    Optional<SubscriptionEntity> existingSubscription =
        subscriptionRepository
            .find(SubscriptionEntity.class, SubscriptionEntity.forContract(contract))
            .stream()
            .findFirst();
    if (existingSubscription.isEmpty()) {
      return createSubscriptionForContract(contract, subscriptionId);
    } else {
      updateSubscriptionForContract(existingSubscription.get(), contract);
      return existingSubscription.get();
    }
  }

  private SubscriptionEntity createSubscriptionForContract(
      ContractEntity contract, String subscriptionId) {
    var subscription = new SubscriptionEntity();
    subscription.setStartDate(contract.getStartDate());
    updateSubscriptionForContract(subscription, contract);
    if (subscriptionId != null) {
      subscription.setSubscriptionId(subscriptionId);
    }
    return subscription;
  }

  private void updateSubscriptionForContract(
      SubscriptionEntity subscription, ContractEntity contract) {
    mapper.updateSubscriptionEntityFromContractEntity(subscription, contract);
    measurementMetricIdTransformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    if (subscription.getSubscriptionMeasurements().size() != contract.getMetrics().size()) {
      measurementMetricIdTransformer.resolveConflictingMetrics(contract);
    }
  }

  @Transactional
  public StatusResponse syncContractByOrgId(String contractOrgSync, boolean isPreCleanup) {
    StatusResponse statusResponse = new StatusResponse();

    try {
      if (isPreCleanup) {
        long deletedRecords = deleteCurrentlyActiveContractsByOrgId(contractOrgSync);
        log.info(
            "Total contract deleted for org {} during pre cleanup {}",
            contractOrgSync,
            deletedRecords);
      }

      PageRequest page = new PageRequest();
      page.setSize(20);
      page.setNumber(0);
      var result =
          partnerApi.getPartnerEntitlements(
              new QueryPartnerEntitlementV1().rhAccountId(contractOrgSync).page(page));
      log.debug(
          "Contracts fetched for org {} from upstream {}", contractOrgSync, result.toString());
      if (Objects.nonNull(result.getContent()) && !result.getContent().isEmpty()) {
        for (PartnerEntitlementV1 entitlement : result.getContent()) {
          if (Objects.nonNull(result.getContent())
              && !result.getContent().isEmpty()
              && Objects.nonNull(entitlement)) {
            var subscriptionId = findSubscriptionNumber(entitlement);
            try {
              upsertPartnerContract(new ContractEntity(), entitlement, subscriptionId);
            } catch (ContractNotAssociatedToOrgException | ContractValidationFailedException e) {
              log.error(
                  "Error synchronising the contract {}. Caused by: {}",
                  entitlement,
                  e.getMessage(),
                  e);
            }
          }
        }
        statusResponse.setMessage("Contracts Synced for " + contractOrgSync);
        statusResponse.setStatus(SUCCESS_MESSAGE);
      } else {
        statusResponse.setMessage("No contracts found in upstream for the org " + contractOrgSync);
        statusResponse.setStatus(FAILURE_MESSAGE);
      }
    } catch (NumberFormatException e) {
      log.error(e.getMessage());
      statusResponse.setStatus(FAILURE_MESSAGE);
      statusResponse.setMessage("An Error occurred while reconciling contract");
      return statusResponse;
    } catch (ProcessingException | ApiException e) {
      log.error(e.getMessage());
      statusResponse.setStatus(FAILURE_MESSAGE);
      statusResponse.setMessage("An Error occurred while calling Partner Api");
      return statusResponse;
    }
    return statusResponse;
  }

  @Transactional
  public StatusResponse deleteContractsByOrgId(String orgId) {
    StatusResponse statusResponse = new StatusResponse();

    List<ContractEntity> contractsToDelete = contractRepository.getContractsByOrgId(orgId);
    contractsToDelete.forEach(this::deleteContract);
    log.info("Deleted {} contract for org id {}", contractsToDelete.size(), orgId);
    statusResponse.setStatus(SUCCESS_MESSAGE);
    return statusResponse;
  }

  private void deleteContract(ContractEntity contract) {
    var subscription =
        subscriptionRepository
            .find(SubscriptionEntity.class, SubscriptionEntity.forContract(contract))
            .stream()
            .findFirst()
            .orElse(null);
    if (contract != null) {
      contractRepository.delete(contract);
    }
    if (subscription != null) {
      subscriptionRepository.delete(subscription);
    }
  }

  private void persistExistingContract(ContractEntity existingContract, OffsetDateTime now) {
    existingContract.setEndDate(now);
    existingContract.setLastUpdated(now);
    contractRepository.persist(existingContract);
  }

  private void persistSubscription(SubscriptionEntity subscription) {
    subscriptionRepository.persist(subscription);
  }

  private void persistContract(ContractEntity entity, OffsetDateTime now) {
    if (entity.getUuid() == null) {
      entity.setUuid(UUID.randomUUID());
    }

    entity.getMetrics().forEach(f -> f.setContractUuid(entity.getUuid()));
    entity.setLastUpdated(now);
    contractRepository.persist(entity);
    log.info("New contract created/updated with UUID {}", entity.getUuid());
  }

  /**
   * Locates an existing contract record based on matching identifiers
   *
   * @param contract contract having identifiers
   * @return existing contract record, or null
   */
  private List<ContractEntity> findExistingContractRecords(ContractEntity contract) {
    var specification = ContractEntity.activeDuringTimeRange(contract);
    if (contract.getBillingProvider().startsWith("aws")) {
      specification =
          specification.and(
              ContractEntity.billingProviderIdEquals(contract.getBillingProviderId()));
    } else if (contract.getBillingProvider().startsWith("azure")) {
      specification =
          specification.and(ContractEntity.azureResourceIdEquals(contract.getAzureResourceId()));
    } else {
      throw new UnsupportedOperationException(
          String.format("Billing provider %s not implemented", contract.getBillingProvider()));
    }
    return contractRepository.findContracts(specification).toList();
  }

  private long deleteCurrentlyActiveContractsByOrgId(String orgId) {
    return contractRepository.deleteContractsByOrgIdForEmptyValues(orgId);
  }

  private void mapUpstreamContractToContractEntity(
      ContractEntity entity, PartnerEntitlementV1 entitlement) {
    Optional.ofNullable(findSubscriptionNumber(entitlement))
        .ifPresent(entity::setSubscriptionNumber);
    if (entitlement != null) {
      if (Objects.equals(entitlement.getSourcePartner(), SourcePartnerEnum.AWS_MARKETPLACE)) {
        entity.setBillingProviderId(
            String.format(
                "%s;%s;%s",
                entitlement.getPurchase().getVendorProductCode(),
                entitlement.getPartnerIdentities().getAwsCustomerId(),
                entitlement.getPartnerIdentities().getSellerAccountId()));
      }
      mapper.mapRhEntitlementsToContractEntity(entity, entitlement);

      var dimensionV1s =
          entitlement.getPurchase().getContracts().stream()
              .filter(contract -> Objects.isNull(contract.getEndDate()))
              .flatMap(contract -> contract.getDimensions().stream())
              .collect(Collectors.toSet());
      entity.addMetrics(mapper.dimensionV1ToContractMetricEntity(dimensionV1s));
      populateProductIdBySku(entity);
      // if the product ID has been populated, we can discard the wrong metrics from the contract
      if (entity.getProductId() != null) {
        measurementMetricIdTransformer.resolveConflictingMetrics(entity);
      }
    } else {
      log.error("No results found from partner entitlement for contract {}", entity);
    }
  }

  private String findSubscriptionNumber(PartnerEntitlementV1 entitlement) {
    if (entitlement != null) {
      return mapper.getRhSubscriptionNumber(entitlement.getRhEntitlements());
    }
    return null;
  }

  private void populateProductIdBySku(ContractEntity entity) {
    var sku = entity.getSku();
    log.trace("Call swatch api to get product tags by sku {}", sku);

    if (Objects.nonNull(sku)) {
      try {
        OfferingProductTags productTags = syncService.getOfferingProductTags(sku);
        if (Objects.nonNull(productTags)
            && Objects.nonNull(productTags.getData())
            && !productTags.getData().isEmpty()
            && Objects.nonNull(productTags.getData().get(0))) {
          entity.setProductId(productTags.getData().get(0));
        } else {
          log.error("Error getting product tags");
        }
      } catch (Exception e) {
        log.error("Unable to connect to swatch api to get product tags");
      }
    }
  }

  private static StatusResponse buildContractDetailsMissingStatus(
      ContractValidationFailedException e) {
    var violations = e.getViolations();
    log.warn("Contract missing required details {}", e.getEntity());
    for (var violation : violations) {
      log.warn("Property {} {}", violation.getPropertyPath(), violation.getMessage());
    }
    return ContractMessageProcessingResult.CONTRACT_DETAILS_MISSING.toStatus();
  }
}
