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

import com.redhat.swatch.clients.rh.partner.gateway.api.model.PageRequest;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1.SourcePartnerEnum;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlements;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.exception.ContractNotAssociatedToOrgException;
import com.redhat.swatch.contract.exception.ContractValidationFailedException;
import com.redhat.swatch.contract.exception.ContractsException;
import com.redhat.swatch.contract.exception.CreateContractException;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.model.ContractMapper;
import com.redhat.swatch.contract.model.ContractSourcePartnerEnum;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.OfferingProductTags;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractRepository;
import com.redhat.swatch.contract.repository.Specification;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
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

  public enum ContractMessageProcessingResult {
    INVALID_MESSAGE_UNPROCESSED,
    RH_ORG_NOT_ASSOCIATED,
    CONTRACT_DETAILS_MISSING,
    PARTNER_API_FAILURE,
    REDUNDANT_MESSAGE_IGNORED,
    METADATA_UPDATED,
    CONTRACT_SPLIT_DUE_TO_CAPACITY_UPDATE,
    NEW_CONTRACT_CREATED,
  }

  private static final String SUCCESS_MESSAGE = "SUCCESS";
  private static final String FAILURE_MESSAGE = "FAILED";

  private final ContractRepository contractRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final ContractMapper mapper;
  private final MeasurementMetricIdTransformer measurementMetricIdTransformer;
  private final SubscriptionSyncService syncService;
  @Inject @RestClient PartnerApi partnerApi;
  @Inject @RestClient SearchApi subscriptionApi;
  @Inject Validator validator;

  ContractService(
      ContractRepository contractRepository,
      SubscriptionRepository subscriptionRepository,
      ContractMapper mapper,
      MeasurementMetricIdTransformer measurementMetricIdTransformer,
      SubscriptionSyncService syncService) {
    this.contractRepository = contractRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.mapper = mapper;
    this.measurementMetricIdTransformer = measurementMetricIdTransformer;
    this.syncService = syncService;
  }

  /**
   * If there's not an already active contract in the database, create a new Contract for the given
   * payload.
   *
   * @param contract the contract dto to create.
   * @return Contract dto
   */
  @Transactional
  public Contract createContract(Contract contract) {

    List<ContractEntity> contracts = listCurrentlyActiveContracts(contract, OffsetDateTime.now());
    log.info("{}", contracts);

    if (!contracts.isEmpty()) {
      var message =
          "There's already an active contract for that productId & subscriptionNumber: " + contract;
      log.error(message);
      throw new CreateContractException(message);
    }

    var uuid = Objects.requireNonNullElse(contract.getUuid(), UUID.randomUUID().toString());
    contract.setUuid(uuid);

    var entity = mapper.dtoToContractEntity(contract);

    var now = OffsetDateTime.now();
    entity.setLastUpdated(now);

    var subscription = createSubscriptionForContract(entity, false);
    subscription.setSubscriptionId(contract.getUuid());
    contractRepository.persist(entity);
    subscriptionRepository.persist(subscription);

    return contract;
  }

  private List<ContractEntity> listCurrentlyActiveContracts(
      Contract contract, OffsetDateTime asOfTimestamp) {
    Specification<ContractEntity> specification =
        ContractEntity.productIdEquals(contract.getProductId())
            .and(ContractEntity.subscriptionNumberEquals(contract.getSubscriptionNumber()))
            .and(ContractEntity.activeOn(asOfTimestamp));
    return contractRepository.getContracts(specification);
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
    return switch (processContract(contract)) {
      case INVALID_MESSAGE_UNPROCESSED ->
          new StatusResponse().message("Bad message, see logs for details").status(FAILURE_MESSAGE);
      case RH_ORG_NOT_ASSOCIATED ->
          new StatusResponse().message("Contract missing RH orgId").status(FAILURE_MESSAGE);
      case CONTRACT_DETAILS_MISSING ->
          new StatusResponse().message("Empty value in non-null fields").status(FAILURE_MESSAGE);
      case PARTNER_API_FAILURE ->
          new StatusResponse()
              .message("An Error occurred while calling Partner Api")
              .status(FAILURE_MESSAGE);
      case REDUNDANT_MESSAGE_IGNORED ->
          new StatusResponse().message("Redundant message ignored").status(SUCCESS_MESSAGE);
      case METADATA_UPDATED ->
          new StatusResponse().message("Contract metadata updated").status(SUCCESS_MESSAGE);
      case CONTRACT_SPLIT_DUE_TO_CAPACITY_UPDATE ->
          new StatusResponse()
              .message("Previous contract archived and new contract created")
              .status(SUCCESS_MESSAGE);
      case NEW_CONTRACT_CREATED ->
          new StatusResponse().message("New contract created").status(SUCCESS_MESSAGE);
    };
  }

  public ContractMessageProcessingResult processContract(PartnerEntitlementContract contract) {
    if (!validPartnerEntitlementContract(contract)) {
      log.info("Empty value found in UMB message {}", contract);
      return ContractMessageProcessingResult.INVALID_MESSAGE_UNPROCESSED;
    }

    ContractEntity entity;
    try {
      // Fill up information from upstream and swatch
      entity = mapper.partnerContractToContractEntity(contract);
      collectMissingUpStreamContractDetails(entity, contract);
    } catch (ContractNotAssociatedToOrgException e) {
      return ContractMessageProcessingResult.RH_ORG_NOT_ASSOCIATED;
    } catch (ContractValidationFailedException e) {
      var violations = e.getViolations();
      log.warn("Contract missing required details {}", e.getEntity());
      for (var violation : violations) {
        log.warn("Property {} {}", violation.getPropertyPath(), violation.getMessage());
      }
      return ContractMessageProcessingResult.CONTRACT_DETAILS_MISSING;
    } catch (NumberFormatException e) {
      log.error(e.getMessage());
      return ContractMessageProcessingResult.INVALID_MESSAGE_UNPROCESSED;
    } catch (ProcessingException | ApiException e) {
      log.error(e.getMessage());
      return ContractMessageProcessingResult.PARTNER_API_FAILURE;
    }

    return upsertPartnerContract(entity);
  }

  private ContractMessageProcessingResult upsertPartnerContract(ContractEntity entity) {
    List<ContractEntity> existingContractRecords = findExistingContractRecords(entity);
    // There may be multiple "versions" of a contract, e.g. if a contract quantity changes.
    // We should make updates as necessary to each, it's possible to update both the metadata and
    // the quantity at once.
    var statuses =
        existingContractRecords.stream()
            .map(existing -> updateContractRecord(existing, entity))
            .toList();
    if (!statuses.isEmpty()) {
      return combineStatuses(statuses);
    } else {
      // New contract
      if (entity.getSubscriptionNumber() != null) {
        persistSubscription(createSubscriptionForContract(entity, true));
      }
      persistContract(entity, OffsetDateTime.now());
      return ContractMessageProcessingResult.NEW_CONTRACT_CREATED;
    }
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
      ContractEntity existingContract, ContractEntity entity) {
    if (Objects.equals(entity, existingContract)) {
      log.info(
          "Duplicate contract found that matches the record for uuid {}",
          existingContract.getUuid());
      return ContractMessageProcessingResult.REDUNDANT_MESSAGE_IGNORED;
    } else if (isContractQuantityChanged(entity, existingContract)) {
      // Record found in contract table but the contract quantities or dates have changed
      if (!Objects.equals(entity.getEndDate(), existingContract.getEndDate())) {
        // Skip this particular record because it's already been terminated for a quantity change.
        return ContractMessageProcessingResult.REDUNDANT_MESSAGE_IGNORED;
      }
      var now = OffsetDateTime.now();
      if (existingContract.getSubscriptionNumber() != null) {
        persistExistingSubscription(existingContract, now); // end current subscription
        var newSubscription = createSubscriptionForContract(entity, true);
        newSubscription.setStartDate(now);
        persistSubscription(newSubscription);
      }
      persistExistingContract(existingContract, now); // Persist previous contract
      entity.setStartDate(now);
      persistContract(entity, now); // Persist new contract
      log.info("Previous contract archived and new contract created");
      return ContractMessageProcessingResult.CONTRACT_SPLIT_DUE_TO_CAPACITY_UPDATE;
    } else {
      // Record found in contract table but a non-quantity change occurred (e.g. an identifier was
      // updated).
      mapper.updateContract(existingContract, entity);
      persistContract(existingContract, OffsetDateTime.now());
      if (existingContract.getSubscriptionNumber() != null) {
        SubscriptionEntity subscription = createOrUpdateSubscription(existingContract);
        subscriptionRepository.persist(subscription);
      }
      log.info("Contract metadata updated");
      return ContractMessageProcessingResult.METADATA_UPDATED;
    }
  }

  private boolean isContractQuantityChanged(
      ContractEntity entity, ContractEntity existingContract) {
    return !Objects.equals(entity.getMetrics(), existingContract.getMetrics());
  }

  private void persistExistingSubscription(ContractEntity contract, OffsetDateTime now) {
    var subscription =
        subscriptionRepository
            .findOne(SubscriptionEntity.class, SubscriptionEntity.forContract(contract))
            .orElseGet(() -> createSubscriptionForContract(contract, true));
    subscription.setEndDate(now);
    subscriptionRepository.persist(subscription);
  }

  private SubscriptionEntity createOrUpdateSubscription(ContractEntity contract) {
    Optional<SubscriptionEntity> existingSubscription =
        subscriptionRepository
            .find(SubscriptionEntity.class, SubscriptionEntity.forContract(contract))
            .stream()
            .findFirst();
    if (existingSubscription.isEmpty()) {
      return createSubscriptionForContract(contract, true);
    } else {
      updateSubscriptionForContract(existingSubscription.get(), contract);
      return existingSubscription.get();
    }
  }

  private SubscriptionEntity createSubscriptionForContract(
      ContractEntity contract, boolean lookupSubscriptionId) {
    var subscription = new SubscriptionEntity();
    subscription.setStartDate(contract.getStartDate());
    updateSubscriptionForContract(subscription, contract);
    if (lookupSubscriptionId) {
      subscription.setSubscriptionId(lookupSubscriptionId(contract.getSubscriptionNumber()));
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

  private String lookupSubscriptionId(String subscriptionNumber) {
    try {
      return subscriptionApi.getSubscriptionBySubscriptionNumber(subscriptionNumber).stream()
          .findFirst()
          .orElseThrow()
          .getId()
          .toString();
    } catch (Exception e) {
      log.error("Error fetching subscription ID for contract", e);
      throw new ContractsException(
          ErrorCode.CONTRACT_DOES_NOT_EXIST, "Unable to lookup subscription for contract");
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
        for (PartnerEntitlementV1 partnerEntitlement : result.getContent()) {
          ContractEntity entity = new ContractEntity();
          if (Objects.nonNull(result.getContent())
              && !result.getContent().isEmpty()
              && Objects.nonNull(partnerEntitlement)) {
            mapSingleUpstreamContractToContractEntity(entity, partnerEntitlement);
            upsertPartnerContract(entity);
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

  private boolean validPartnerEntitlementContract(PartnerEntitlementContract contract) {
    return validAwsEntitlementContract(contract) || validAzureEntitlementContract(contract);
  }

  private boolean validAzureEntitlementContract(PartnerEntitlementContract contract) {
    return Objects.nonNull(contract.getCloudIdentifiers())
        && Objects.nonNull(contract.getCloudIdentifiers().getAzureResourceId());
  }

  private boolean validAwsEntitlementContract(PartnerEntitlementContract contract) {
    return Objects.nonNull(contract.getRedHatSubscriptionNumber())
        && Objects.nonNull(contract.getCloudIdentifiers())
        && Objects.nonNull(contract.getCloudIdentifiers().getAwsCustomerId())
        && Objects.nonNull(contract.getCloudIdentifiers().getProductCode());
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

  // Retry, except when the org has not yet been associated, as that requires some lengthy backend
  // processing, and there will be a follow-up message.
  @Retry(delay = 500, maxRetries = 10, abortOn = ContractNotAssociatedToOrgException.class)
  public void collectMissingUpStreamContractDetails( // NOSONAR
      ContractEntity entity, PartnerEntitlementContract contract)
      throws ApiException, ContractValidationFailedException, ContractNotAssociatedToOrgException {
    String customerAccountId;
    String productCode;
    var marketplace = determineMarketplaceForContract(contract);

    if (Objects.equals(marketplace, ContractSourcePartnerEnum.AWS.getValue())) {
      customerAccountId = contract.getCloudIdentifiers().getAwsCustomerAccountId();
      productCode = contract.getCloudIdentifiers().getProductCode();
      if (Objects.nonNull(contract.getCloudIdentifiers())
          && Objects.nonNull(customerAccountId)
          && Objects.nonNull(productCode)) {
        PageRequest page = new PageRequest();
        page.setSize(20);
        page.setNumber(0);
        log.trace(
            "Call Partner Api to fill missing information using customerAwsAccountId {} and vendorProductCode {}",
            customerAccountId,
            productCode);
        var result =
            partnerApi.getPartnerEntitlements(
                new QueryPartnerEntitlementV1()
                    .customerAwsAccountId(customerAccountId)
                    .vendorProductCode(productCode)
                    .page(page));
        mapUpstreamContractsToContractEntity(entity, result);
      }
    }
    if (Objects.equals(marketplace, ContractSourcePartnerEnum.AZURE.getValue())) {
      // azureResourceId is a unique identifier per SaaS purchase,
      // so it should be sufficient by itself
      customerAccountId = contract.getCloudIdentifiers().getAzureResourceId();
      if (Objects.nonNull(contract.getCloudIdentifiers()) && Objects.nonNull(customerAccountId)) {
        // get the entitlement query from partner api for azure marketplace
        PageRequest page = new PageRequest();
        page.setSize(20);
        page.setNumber(0);
        log.trace(
            "Call Partner Api to fill missing information using Azure resourceId {}",
            customerAccountId);
        var result =
            partnerApi.getPartnerEntitlements(
                new QueryPartnerEntitlementV1().azureResourceId(customerAccountId).page(page));
        mapUpstreamContractsToContractEntity(entity, result);
      }
    }
    if (entity.getOrgId() == null) {
      throw new ContractNotAssociatedToOrgException();
    }
    var violations = validator.validate(entity);
    if (!violations.isEmpty()) {
      throw new ContractValidationFailedException(entity, violations);
    }
  }

  private String determineMarketplaceForContract(PartnerEntitlementContract contract) {
    if (Objects.nonNull(contract.getCloudIdentifiers().getAwsCustomerAccountId())) {
      return "aws";
    } else if (Objects.nonNull(contract.getCloudIdentifiers().getAzureResourceId())) {
      return "azure";
    }
    return null;
  }

  private void mapUpstreamContractsToContractEntity(
      ContractEntity entity, PartnerEntitlements result) {
    if (Objects.nonNull(result.getContent())
        && !result.getContent().isEmpty()
        && Objects.nonNull(result.getContent().get(0))) {
      mapSingleUpstreamContractToContractEntity(entity, result.getContent().get(0));
    } else {
      log.error("No results found from partner entitlement for contract {}", entity.toString());
    }
  }

  private void mapSingleUpstreamContractToContractEntity(
      ContractEntity entity, PartnerEntitlementV1 entitlement) {
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
    if (Objects.isNull(entity.getSubscriptionNumber())) {
      entity.setSubscriptionNumber(mapper.getRhSubscriptionNumber(entitlement.getRhEntitlements()));
    }
    populateProductIdBySku(entity);
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
}
