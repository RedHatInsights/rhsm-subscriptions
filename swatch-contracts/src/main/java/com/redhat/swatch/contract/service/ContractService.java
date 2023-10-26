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
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlements;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
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
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Service layer for interfacing with database and external APIs for manipulation of swatch Contract
 * records
 */
@Slf4j
@ApplicationScoped
public class ContractService {

  private static final String SUCCESS_MESSAGE = "SUCCESS";
  private static final String FAILURE_MESSAGE = "FAILED";

  private final ContractRepository contractRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final ContractMapper mapper;
  private final MeasurementMetricIdTransformer measurementMetricIdTransformer;
  private final SubscriptionSyncService syncService;
  @Inject @RestClient PartnerApi partnerApi;
  @Inject @RestClient SearchApi subscriptionApi;

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
   * payload. This method will always set the end date to 'null', which indicates an active
   * contract.
   *
   * @param contract the contract dto to create.
   * @return Contract dto
   */
  @Transactional
  public Contract createContract(Contract contract) {

    List<ContractEntity> contracts = listCurrentlyActiveContracts(contract);
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

    // Force end date to be null to indicate this it the current/applicable record
    if (Objects.nonNull(contract.getEndDate())) {
      log.warn("Ignoring end date from payload and saving as null");
    }
    entity.setEndDate(null);

    var subscription = createSubscriptionForContract(entity, false);
    subscription.setSubscriptionId(contract.getUuid());
    contractRepository.persist(entity);
    subscriptionRepository.persist(subscription);

    return contract;
  }

  private List<ContractEntity> listCurrentlyActiveContracts(Contract contract) {
    Specification<ContractEntity> specification =
        ContractEntity.productIdEquals(contract.getProductId())
            .and(ContractEntity.subscriptionNumberEquals(contract.getSubscriptionNumber()))
            .and(ContractEntity.isActive());
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
    StatusResponse statusResponse = new StatusResponse();

    if (!validPartnerEntitlementContract(contract)) {
      statusResponse.setMessage("Empty value found in UMB message");
      log.info("Empty value found in UMB message {}", contract);
      return statusResponse;
    }

    ContractEntity entity;
    try {
      // Fill up information from upstream and swatch
      entity = mapper.partnerContractToContractEntity(contract);
      collectMissingUpStreamContractDetails(entity, contract);
      if (!isValidEntity(entity)) {
        statusResponse.setMessage("Empty value in non-null fields");
        log.warn("Empty value in non-null fields for contract entity {}", entity);
        return statusResponse;
      }
    } catch (NumberFormatException e) {
      log.error(e.getMessage());
      statusResponse.setMessage("An Error occurred while reconciling contract");
      return statusResponse;
    } catch (ProcessingException | ApiException e) {
      log.error(e.getMessage());
      statusResponse.setMessage("An Error occurred while calling Partner Api");
      return statusResponse;
    }

    Optional<ContractEntity> existing = currentlyActiveContract(entity);
    boolean isDuplicateContract;
    if (existing.isPresent()) {
      ContractEntity existingContract = existing.get();
      isDuplicateContract = isDuplicateContract(entity, existingContract);
      if (isDuplicateContract) {
        log.info(
            "Duplicate contract found that matches the record for uuid {}",
            existingContract.getUuid());
        statusResponse.setMessage("Duplicate record found");
      } else {
        // Record found in contract table but, the contract has changed
        var now = OffsetDateTime.now();
        persistExistingSubscription(existingContract, now); // end current subscription
        persistExistingContract(existingContract, now); // Persist previous contract

        persistContract(entity, now); // Persist new contract
        log.info("Previous contract archived and new contract created");
        statusResponse.setMessage("Previous contract archived and new contract created");
      }
    } else {
      // New contract
      var now = OffsetDateTime.now();
      persistSubscription(createSubscriptionForContract(entity, true), now);
      persistContract(entity, now);
      statusResponse.setMessage("New contract created");
    }

    return statusResponse;
  }

  private void persistExistingSubscription(ContractEntity contract, OffsetDateTime now) {
    var subscription =
        subscriptionRepository
            .findOne(SubscriptionEntity.class, SubscriptionEntity.forContract(contract))
            .orElseGet(() -> createSubscriptionForContract(contract, true));
    subscription.setEndDate(now);
    subscriptionRepository.persist(subscription);
  }

  private SubscriptionEntity createSubscriptionForContract(
      ContractEntity contract, boolean lookupSubscriptionId) {
    var subscription = new SubscriptionEntity();
    mapper.mapContractEntityToSubscriptionEntity(subscription, contract);
    measurementMetricIdTransformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    if (subscription.getSubscriptionMeasurements().size() != contract.getMetrics().size()) {
      measurementMetricIdTransformer.resolveConflictingMetrics(contract);
    }
    if (lookupSubscriptionId) {
      subscription.setSubscriptionId(lookupSubscriptionId(contract.getSubscriptionNumber()));
    }
    return subscription;
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
  public StatusResponse syncContractByOrgId(String contractOrgSync) {
    StatusResponse statusResponse = new StatusResponse();

    try {
      var currentContracts = listCurrentlyActiveContractsByOrgId(contractOrgSync);

      if (currentContracts.isEmpty()) {
        log.debug("No active contract for {}", contractOrgSync);
        return statusResponse
            .status(FAILURE_MESSAGE)
            .message(contractOrgSync + " not found in table");
      }

      for (ContractEntity contract : currentContracts) {
        var result =
            partnerApi.getPartnerEntitlements(
                new QueryPartnerEntitlementV1()
                    .rhAccountId(contract.getOrgId())
                    .customerAwsAccountId(contract.getBillingAccountId())
                    .vendorProductCode(contract.getVendorProductCode())
                    .source(contract.getBillingProvider()));
        if (Objects.nonNull(result.getContent()) && !result.getContent().isEmpty()) {
          var entitlementEntity =
              transformEntitlementToContractEntity(result.getContent(), contract);
          if (Objects.nonNull(entitlementEntity)) {
            log.info("Syncing new Contract for {}", contractOrgSync);
            statusResponse.setStatus(SUCCESS_MESSAGE);
            var now = OffsetDateTime.now();
            persistSubscription(createSubscriptionForContract(entitlementEntity, true), now);
            persistContract(entitlementEntity, now);
          } else {
            statusResponse.setStatus(FAILURE_MESSAGE);
            statusResponse.setMessage("Entitlement Cannot be found for " + contractOrgSync);
            return statusResponse;
          }
        }
      }
      statusResponse.setMessage("Contracts Synced for " + contractOrgSync);
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
    return Objects.nonNull(contract.getRedHatSubscriptionNumber())
        && Objects.nonNull(contract.getCloudIdentifiers())
        && Objects.nonNull(contract.getCloudIdentifiers().getAzureResourceId())
        && Objects.nonNull(contract.getCloudIdentifiers().getOfferId());
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

  private boolean isValidEntity(ContractEntity entity) {
    // Check all non-null fields
    return Objects.nonNull(entity)
        && Objects.nonNull(entity.getSubscriptionNumber())
        && Objects.nonNull(entity.getOrgId())
        && Objects.nonNull(entity.getSku())
        && Objects.nonNull(entity.getBillingProvider())
        && Objects.nonNull(entity.getBillingAccountId())
        && Objects.nonNull(entity.getProductId())
        && Objects.nonNull(entity.getMetrics())
        && !entity.getMetrics().isEmpty();
  }

  private void persistSubscription(SubscriptionEntity subscription, OffsetDateTime now) {
    subscription.setStartDate(now);
    subscriptionRepository.persist(subscription);
  }

  private void persistContract(ContractEntity entity, OffsetDateTime now) {
    if (entity.getUuid() == null) {
      entity.setUuid(UUID.randomUUID());
    }

    entity.getMetrics().forEach(f -> f.setContractUuid(entity.getUuid()));
    entity.setStartDate(now);
    entity.setLastUpdated(now);
    contractRepository.persist(entity);
    log.info("New contract created/updated with UUID {}", entity.getUuid());
  }

  private Optional<ContractEntity> currentlyActiveContract(ContractEntity contract) {
    var specification =
        ContractEntity.subscriptionNumberEquals(contract.getSubscriptionNumber())
            .and(ContractEntity.isActive());
    return contractRepository.getContracts(specification).stream().findFirst();
  }

  private List<ContractEntity> listCurrentlyActiveContractsByOrgId(String orgId) {
    Specification<ContractEntity> specification =
        ContractEntity.orgIdEquals(orgId).and(ContractEntity.isActive());
    return contractRepository.getContracts(specification);
  }

  private boolean isDuplicateContract(ContractEntity newEntity, ContractEntity existing) {
    return Objects.equals(newEntity, existing);
  }

  private ContractEntity transformEntitlementToContractEntity( // NOSONAR
      List<PartnerEntitlementV1> entitlements, ContractEntity prevEntity) throws ApiException {
    if (Objects.nonNull(entitlements) && Objects.nonNull(entitlements.get(0))) {
      // This is so that current entities have the other fields not being updated here,
      // so it can be persisted without missing information
      var entitlement = entitlements.get(0);

      if (Objects.nonNull(prevEntity.getEndDate())
          && Objects.nonNull(entitlement.getEntitlementDates())
          && prevEntity.getEndDate().isAfter(entitlement.getEntitlementDates().getEndDate())) {
        log.debug("This Contract is No longer active for {}", prevEntity.getOrgId());
        return prevEntity;
      }

      prevEntity.setOrgId(entitlement.getRhAccountId());
      prevEntity.setBillingProvider(entitlement.getSourcePartner().value());
      var partnerIdentity = entitlement.getPartnerIdentities();
      if (Objects.nonNull(partnerIdentity)) {
        prevEntity.setBillingAccountId(partnerIdentity.getCustomerAwsAccountId());

        var rhEntitlements = entitlement.getRhEntitlements();
        if (Objects.nonNull(rhEntitlements)
            && !rhEntitlements.isEmpty()
            && Objects.nonNull(rhEntitlements.get(0))) {
          var subscription = rhEntitlements.get(0).getSubscriptionNumber();
          var sku = rhEntitlements.get(0).getSku();
          prevEntity.setSku(sku);
          prevEntity.setSubscriptionNumber(subscription);
          OfferingProductTags productTags = syncService.getOfferingProductTags(sku);
          if (Objects.nonNull(productTags.getData())
              && Objects.nonNull(productTags.getData().get(0))) {
            prevEntity.setProductId(productTags.getData().get(0));
          } else {
            log.error("Error getting product tags");
          }
        }
      }
      return prevEntity;
    }
    return null;
  }

  private void collectMissingUpStreamContractDetails( // NOSONAR
      ContractEntity entity, PartnerEntitlementContract contract) throws ApiException {
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
        mapUpstreamContractToContractEntity(entity, result);
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
        mapUpstreamContractToContractEntity(entity, result);
      }
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

  private void mapUpstreamContractToContractEntity(
      ContractEntity entity, PartnerEntitlements result) {
    if (Objects.nonNull(result.getContent())
        && !result.getContent().isEmpty()
        && Objects.nonNull(result.getContent().get(0))) {
      var entitlement = result.getContent().get(0);

      mapper.mapRhEntitlementsToContractEntity(entity, entitlement);
      entity.setBillingProvider(
          ContractSourcePartnerEnum.getByCode(entitlement.getSourcePartner().value()));

      var dimensionV1s =
          entitlement.getPurchase().getContracts().stream()
              .filter(contract -> Objects.isNull(contract.getEndDate()))
              .flatMap(contract -> contract.getDimensions().stream())
              .collect(Collectors.toSet());
      entity.setMetrics(mapper.dimensionV1ToContractMetricEntity(dimensionV1s));
      populateProductIdBySku(entity);
    }
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
