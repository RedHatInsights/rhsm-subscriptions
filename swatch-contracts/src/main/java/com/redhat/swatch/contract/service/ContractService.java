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

import static com.redhat.swatch.contract.model.ContractSourcePartnerEnum.isAwsMarketplace;
import static com.redhat.swatch.contract.utils.ContractMessageProcessingResult.INVALID_MESSAGE_UNPROCESSED;
import static com.redhat.swatch.contract.utils.ContractMessageProcessingResult.NEW_CONTRACT_CREATED;
import static com.redhat.swatch.contract.utils.ContractMessageProcessingResult.PARTNER_API_FAILURE;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.PageRequest;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import com.redhat.swatch.contract.exception.ContractNotAssociatedToOrgException;
import com.redhat.swatch.contract.exception.ContractValidationFailedException;
import com.redhat.swatch.contract.exception.ContractsException;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.model.ContractDtoMapper;
import com.redhat.swatch.contract.model.ContractEntityMapper;
import com.redhat.swatch.contract.model.ContractSourcePartnerEnum;
import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.model.PartnerEntitlementsRequest;
import com.redhat.swatch.contract.model.SubscriptionEntityMapper;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.ContractRequest;
import com.redhat.swatch.contract.openapi.model.ContractResponse;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.*;
import com.redhat.swatch.contract.utils.ContractMessageProcessingResult;
import com.redhat.swatch.panache.Specification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import jakarta.ws.rs.ProcessingException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
  private final ContractMetricRepository contractMetricRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionMeasurementRepository subscriptionMeasurementRepository;
  private final MeasurementMetricIdTransformer measurementMetricIdTransformer;
  @Inject ContractEntityMapper contractEntityMapper;
  @Inject ContractDtoMapper contractDtoMapper;
  @Inject SubscriptionEntityMapper subscriptionEntityMapper;
  @Inject @RestClient PartnerApi partnerApi;
  @Inject @RestClient SearchApi subscriptionApi;
  @Inject Validator validator;
  private final List<BasePartnerEntitlementsProvider> partnerEntitlementsProviders;

  ContractService(
      ContractRepository contractRepository,
      ContractMetricRepository contractMetricRepository,
      SubscriptionRepository subscriptionRepository,
      SubscriptionMeasurementRepository subscriptionMeasurementRepository,
      MeasurementMetricIdTransformer measurementMetricIdTransformer,
      AwsPartnerEntitlementsProvider awsPartnerEntitlementsProvider,
      AzurePartnerEntitlementsProvider azurePartnerEntitlementsProvider) {
    this.contractRepository = contractRepository;
    this.contractMetricRepository = contractMetricRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionMeasurementRepository = subscriptionMeasurementRepository;
    this.measurementMetricIdTransformer = measurementMetricIdTransformer;
    this.partnerEntitlementsProviders =
        List.of(awsPartnerEntitlementsProvider, azurePartnerEntitlementsProvider);
  }

  @Transactional
  public ContractResponse createContract(ContractRequest request) {
    ContractResponse response = new ContractResponse();
    if (findPartnerEntitlementsProvider(PartnerEntitlementsRequest.from(request)) == null) {
      log.info("Can't process the contract because is not contract-enabled: {}", request);
      response.setStatus(INVALID_MESSAGE_UNPROCESSED.toStatus());
      return response;
    }

    try {
      var result =
          upsertPartnerContracts(request.getPartnerEntitlement(), request.getSubscriptionId());
      response.setStatus(result.toStatus());
      if (result.isValid() && result.getEntity() != null) {
        response.setContract(contractDtoMapper.contractEntityToDto(result.getEntity()));
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
   * @param productTag the product tag.
   * @param billingProvider the billing provider.
   * @param billingAccountId the billing account ID.
   * @param vendorProductCode the vendor product code.
   * @return List<Contract> the list of contracts.
   */
  public List<Contract> getContracts(
      String orgId,
      String productTag,
      String billingProvider,
      String billingAccountId,
      String vendorProductCode,
      OffsetDateTime timestamp) {

    Specification<ContractEntity> specification = ContractEntity.orgIdEquals(orgId);

    if (productTag != null) {
      specification = specification.and(ContractEntity.productTagEquals(productTag));
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
        .map(contractDtoMapper::contractEntityToDto)
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
    var request = PartnerEntitlementsRequest.from(contract);
    var partnerEntitlementsProvider = findPartnerEntitlementsProvider(request);
    if (partnerEntitlementsProvider == null) {
      log.info("Can't process the contract from UMB: {}", contract);
      return INVALID_MESSAGE_UNPROCESSED.toStatus();
    }

    try {
      return callPartnerApiAndUpsertPartnerContract(request, partnerEntitlementsProvider);
    } catch (ProcessingException | ApiException e) {
      log.error(e.getMessage());
      return PARTNER_API_FAILURE.toStatus();
    } catch (ContractValidationFailedException e) {
      return buildContractDetailsMissingStatus(e);
    }
  }

  @Retry(delay = 500, maxRetries = 10, abortOn = ContractNotAssociatedToOrgException.class)
  public StatusResponse callPartnerApiAndUpsertPartnerContract(
      PartnerEntitlementsRequest request,
      BasePartnerEntitlementsProvider partnerEntitlementsProvider)
      throws ApiException, ContractValidationFailedException {
    try {
      var entitlement = partnerEntitlementsProvider.getPartnerEntitlement(request);
      if (entitlement == null) {
        log.error("No results found from partner entitlement for contract {}", request);
        return INVALID_MESSAGE_UNPROCESSED.toStatus();
      }

      var subscriptionId =
          lookupSubscriptionId(
              Optional.ofNullable(findSubscriptionNumber(entitlement))
                  .orElse(request.getRedHatSubscriptionNumber()));
      return upsertPartnerContracts(entitlement, subscriptionId).toStatus();
    } catch (ContractNotAssociatedToOrgException e) {
      return ContractMessageProcessingResult.RH_ORG_NOT_ASSOCIATED.toStatus();
    }
  }

  /**
   * Update or create contract and subscription database records for each Contract in the
   * PartnerEntitlement. Updates are made when the database records have matching start_dates,
   * otherwise a new record will be created. If no matching PartnerEntitlement Contract is found
   * then the existing record is deleted.
   */
  @Transactional
  public ContractMessageProcessingResult upsertPartnerContracts(
      PartnerEntitlementV1 entitlement, String subscriptionId)
      throws ContractNotAssociatedToOrgException, ContractValidationFailedException {
    List<ContractEntity> entities;

    try {
      entities = mapUpstreamContractToContractEntities(entitlement);
    } catch (NumberFormatException e) {
      log.error(e.getMessage());
      return INVALID_MESSAGE_UNPROCESSED;
    }

    var latestContract = getLatestContract(entities);

    List<ContractEntity> existingContractRecords = findExistingContractRecords(latestContract);

    if (existingContractRecords.equals(entities)) {
      return ContractMessageProcessingResult.REDUNDANT_MESSAGE_IGNORED.withContract(latestContract);
    }

    mergeWithExistingContractRecords(entities, existingContractRecords);

    if (latestContract.getSubscriptionNumber() != null) {
      mergeWithExistingSubscriptionRecords(entities, subscriptionId);
    }

    if (existingContractRecords.contains(latestContract)) {
      return ContractMessageProcessingResult.EXISTING_CONTRACTS_SYNCED.withContract(latestContract);
    }

    return NEW_CONTRACT_CREATED.withContract(latestContract);
  }

  private void mergeWithExistingContractRecords(
      List<ContractEntity> unsavedContracts, List<ContractEntity> existingContracts) {
    Set<ContractEntity> contractsToPersist = new HashSet<>();

    // Determine matching contracts based on start_date
    Map<Instant, ContractEntity> contractStartDateMap =
        unsavedContracts.stream()
            .collect(
                Collectors.toMap(
                    contract -> contract.getStartDate().toInstant(),
                    Function.identity(),
                    (contract1, contract2) -> {
                      log.warn("Duplicate contracts found. Skipping contract: {}", contract2);
                      return contract1;
                    }));

    existingContracts.forEach(
        existingContract -> {
          var matchingUpdatedContract =
              contractStartDateMap.remove(existingContract.getStartDate().toInstant());

          if (matchingUpdatedContract == null) {
            log.info(
                "Deleting contract that does not align to IT partner gateway: {}",
                existingContract);
            contractRepository.delete(existingContract);
          } else {
            if (!matchingUpdatedContract.equals(existingContract)) {
              log.info(
                  "Contract updated. Old values: {} New values: {}",
                  existingContract,
                  matchingUpdatedContract);
              if (existingContract.getMetrics() != matchingUpdatedContract.getMetrics()) {
                var metrics =
                    existingContract.getMetrics().stream()
                        .filter(metric -> !matchingUpdatedContract.getMetrics().contains(metric))
                        .toList();
                metrics.forEach(metric -> deleteContractMetric(existingContract, metric));
              }
              contractEntityMapper.updateContract(existingContract, matchingUpdatedContract);
              contractsToPersist.add(existingContract);
            } else {
              log.debug("No change in contract: {}", existingContract);
            }
          }
        });

    // If not found in existing Contracts then create new ones.
    contractsToPersist.addAll(contractStartDateMap.values());

    contractsToPersist.forEach(
        contractEntity -> {
          log.info("Updating or creating contract: {}", contractEntity);
          persistContract(contractEntity, OffsetDateTime.now());
        });
  }

  private void mergeWithExistingSubscriptionRecords(
      List<ContractEntity> contractEntities, String subscriptionId) {

    List<SubscriptionEntity> updatedSubscriptions =
        contractEntities.stream()
            .map(entity -> createSubscriptionForContract(entity, subscriptionId))
            .toList();

    Set<SubscriptionEntity> subscriptionsToPersist = new HashSet<>();

    // Determine matching subscription based on start_date
    Map<Instant, SubscriptionEntity> subscriptionStartDateMap =
        updatedSubscriptions.stream()
            .collect(
                Collectors.toMap(
                    subscriptionEntity -> subscriptionEntity.getStartDate().toInstant(),
                    Function.identity(),
                    (sub1, sub2) -> {
                      log.warn("Duplicate contracts found. Skipping subscription: {}", sub2);
                      return sub1;
                    }));

    var existingSubscriptionRecords =
        subscriptionRepository.findBySubscriptionNumber(
            contractEntities.get(0).getSubscriptionNumber());

    existingSubscriptionRecords.forEach(
        existingSubscription -> {
          var matchingUpdatedSubscription =
              subscriptionStartDateMap.remove(existingSubscription.getStartDate().toInstant());

          if (matchingUpdatedSubscription == null) {
            log.info(
                "Deleting subscription that does not align to IT partner gateway: {}",
                existingSubscription);
            subscriptionRepository.delete(existingSubscription);
          } else {
            if (!matchingUpdatedSubscription.equals(existingSubscription)
                || !subscriptionMeasurementsEqual(
                    existingSubscription, matchingUpdatedSubscription)) {
              log.info(
                  "Subscription updated. Old values: {} New values: {}",
                  existingSubscription,
                  matchingUpdatedSubscription);
              if (!subscriptionMeasurementsEqual(
                  existingSubscription, matchingUpdatedSubscription)) {
                deleteMisalignedSubscriptionMeasurements(
                    existingSubscription, matchingUpdatedSubscription);
              }
              subscriptionEntityMapper.updateSubscription(
                  existingSubscription, matchingUpdatedSubscription);
              subscriptionsToPersist.add(existingSubscription);
            } else {
              log.debug("No change in subscription: {}", existingSubscription);
            }
          }
        });

    // If not found in existing subscriptions then create new ones.
    subscriptionsToPersist.addAll(subscriptionStartDateMap.values());

    log.info("Persisting subscriptions: {}", subscriptionsToPersist);
    subscriptionRepository.persist(subscriptionsToPersist);
  }

  private ContractEntity getLatestContract(List<ContractEntity> contracts)
      throws ContractValidationFailedException {
    return contracts.stream()
        .max(Comparator.comparing(ContractEntity::getStartDate))
        .orElseThrow(ContractValidationFailedException::new);
  }

  private void deleteContractMetric(
      ContractEntity contractEntity, ContractMetricEntity contractMetricEntity) {
    log.info("Deleting contract_metric: {} on contract: {}", contractMetricEntity, contractEntity);
    contractMetricRepository.delete(contractMetricEntity);
    contractEntity.removeMetric(contractMetricEntity);
    contractMetricRepository.flush();
  }

  private void deleteMisalignedSubscriptionMeasurements(
      SubscriptionEntity existingSubscription, SubscriptionEntity matchingUpdatedSubscription) {
    var measurements =
        existingSubscription.getSubscriptionMeasurements().stream()
            .filter(
                measurement ->
                    !matchingUpdatedSubscription
                        .getSubscriptionMeasurements()
                        .contains(measurement))
            .toList();
    measurements.forEach(
        measurement -> deleteSubscriptionMeasurement(existingSubscription, measurement));
  }

  private void deleteSubscriptionMeasurement(
      SubscriptionEntity subscriptionEntity,
      SubscriptionMeasurementEntity subscriptionMeasurementEntity) {
    log.info(
        "Deleting subscription_measurement: {} on subscription: {}",
        subscriptionMeasurementEntity,
        subscriptionEntity);
    subscriptionMeasurementRepository.delete(subscriptionMeasurementEntity);
    subscriptionEntity.removeMeasurement(subscriptionMeasurementEntity);
    subscriptionMeasurementRepository.flush();
  }

  private boolean subscriptionMeasurementsEqual(
      SubscriptionEntity existing, SubscriptionEntity updated) {
    return Objects.equals(
        existing.getSubscriptionMeasurements(), updated.getSubscriptionMeasurements());
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
          if (entitlement != null
              && ContractSourcePartnerEnum.isSupported(entitlement.getSourcePartner())) {
            tryUpsertPartnerContract(entitlement);
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
  public StatusResponse syncSubscriptionsForContractsByOrg(String orgId) {
    StatusResponse statusResponse = new StatusResponse();

    contractRepository
        .findContracts(ContractEntity.orgIdEquals(orgId))
        // we only want to update existing subscriptions, so we don't need to provide the
        // subscriptionId here.
        .forEach(contract -> syncSubscriptionForContract(contract, null));

    statusResponse.setStatus(SUCCESS_MESSAGE);
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

  private void tryUpsertPartnerContract(PartnerEntitlementV1 entitlement) {
    var subscriptionId = lookupSubscriptionId(findSubscriptionNumber(entitlement));
    try {
      upsertPartnerContracts(entitlement, subscriptionId);
    } catch (ContractNotAssociatedToOrgException | ContractValidationFailedException e) {
      log.error(
          "Error synchronising the contract {}. Caused by: {}", entitlement, e.getMessage(), e);
    }
  }

  private BasePartnerEntitlementsProvider findPartnerEntitlementsProvider(
      PartnerEntitlementsRequest request) {
    for (BasePartnerEntitlementsProvider provider : partnerEntitlementsProviders) {
      if (provider.isFor(request)) {
        return provider;
      }
    }

    return null;
  }

  private void syncSubscriptionForContract(ContractEntity existingContract, String subscriptionId) {
    if (existingContract.getSubscriptionNumber() != null) {

      log.debug("Synchronizing the subscription for contract {}", existingContract);
      SubscriptionEntity subscription =
          createOrUpdateSubscription(existingContract, subscriptionId);
      subscriptionRepository.persist(subscription);
    }
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
    subscriptionEntityMapper.mapSubscriptionEntityFromContractEntity(subscription, contract);
    measurementMetricIdTransformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    if (subscription.getSubscriptionMeasurements().size() != contract.getMetrics().size()) {
      measurementMetricIdTransformer.resolveConflictingMetrics(contract);
    }
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
    Specification<ContractEntity> specification;
    if (contract.getBillingProvider().startsWith("aws")) {
      specification = ContractEntity.billingProviderIdEquals(contract.getBillingProviderId());
    } else if (contract.getBillingProvider().startsWith("azure")) {
      specification = ContractEntity.azureResourceIdEquals(contract.getAzureResourceId());
    } else {
      throw new UnsupportedOperationException(
          String.format("Billing provider %s not implemented", contract.getBillingProvider()));
    }
    return contractRepository.findContracts(specification).toList();
  }

  private long deleteCurrentlyActiveContractsByOrgId(String orgId) {
    return contractRepository.deleteContractsByOrgIdForEmptyValues(orgId);
  }

  private List<ContractEntity> mapUpstreamContractToContractEntities(
      PartnerEntitlementV1 entitlement)
      throws ContractNotAssociatedToOrgException, ContractValidationFailedException {

    List<ContractEntity> contractEntities = new ArrayList<>();

    if (entitlement.getPurchase() == null) {
      log.warn("Entitlement purchase is null for {}", entitlement);
      throw new ContractValidationFailedException();
    }

    if (entitlement.getPurchase().getContracts() != null) {
      contractEntities =
          entitlement.getPurchase().getContracts().stream()
              .map(
                  contract ->
                      contractEntityMapper.mapEntitlementToContractEntity(entitlement, contract))
              .toList();
    }

    if (contractEntities.isEmpty()) {
      contractEntities =
          List.of(contractEntityMapper.mapEntitlementToContractEntity(entitlement, null));
    }

    if (isAwsMarketplace(entitlement.getSourcePartner())) {
      var billingProviderId =
          String.format(
              "%s;%s;%s",
              entitlement.getPurchase().getVendorProductCode(),
              entitlement.getPartnerIdentities().getAwsCustomerId(),
              entitlement.getPartnerIdentities().getSellerAccountId());
      contractEntities.forEach(
          contractEntity -> contractEntity.setBillingProviderId(billingProviderId));
    }

    contractEntities.forEach(measurementMetricIdTransformer::resolveConflictingMetrics);

    for (ContractEntity entity : contractEntities) {
      if (entity.getOrgId() == null) {
        throw new ContractNotAssociatedToOrgException();
      }
      var violations = validator.validate(entity);
      if (!violations.isEmpty()) {
        throw new ContractValidationFailedException(entity, violations);
      }
    }
    return contractEntities;
  }

  private String lookupSubscriptionId(String subscriptionNumber) {
    if (subscriptionNumber == null) {
      return null;
    }

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

  private String findSubscriptionNumber(PartnerEntitlementV1 entitlement) {
    if (entitlement != null) {
      return contractEntityMapper.extractSubscriptionNumber(entitlement.getRhEntitlements());
    }
    return null;
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
