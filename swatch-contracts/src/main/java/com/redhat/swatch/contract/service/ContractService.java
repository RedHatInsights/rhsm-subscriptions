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
import com.redhat.swatch.contract.exception.ContractMissingException;
import com.redhat.swatch.contract.exception.CreateContractException;
import com.redhat.swatch.contract.model.ContractMapper;
import com.redhat.swatch.contract.model.ContractSourcePartnerEnum;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.OfferingProductTags;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractRepository;
import com.redhat.swatch.contract.repository.Specification;
import com.redhat.swatch.contract.resource.SubscriptionSyncResource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Service layer for interfacing with database and external APIs for manipulation of swatch Contract
 * records
 */
@Slf4j
@ApplicationScoped
public class ContractService {

  private final ContractRepository contractRepository;
  private final ContractMapper mapper;
  private final SubscriptionSyncResource syncResource;
  @Inject @RestClient PartnerApi partnerApi;

  ContractService(
      ContractRepository contractRepository,
      ContractMapper mapper,
      SubscriptionSyncResource syncResource) {
    this.contractRepository = contractRepository;
    this.mapper = mapper;
    this.syncResource = syncResource;
  }

  /**
   * If there's not an already active contract in the database, create a new Contract for the given
   * payload. This method will always set the end date to 'null', which indicates an active
   * contract.
   *
   * @param contract
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

    contractRepository.persist(entity);

    return contract;
  }

  private List<ContractEntity> listCurrentlyActiveContracts(Contract contract) {
    Specification<ContractEntity> specification =
        ContractEntity.productIdEquals(contract.getProductId())
            .and(ContractEntity.subscriptionNumberEquals(contract.getSubscriptionNumber()))
            .and(ContractEntity.isActive());
    return contractRepository.getContracts(specification);
  }

  /**
   * Build Specifications based on provided parameters if not null and use to query the database
   * based on specifications.
   *
   * @param orgId
   * @param productId
   * @param metricId
   * @param billingProvider
   * @param billingAccountId
   * @return List<Contract> dtos
   */
  public List<Contract> getContracts(
      String orgId,
      String productId,
      String metricId,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp) {

    Specification<ContractEntity> specification = ContractEntity.orgIdEquals(orgId);

    if (productId != null) {
      specification = specification.and(ContractEntity.productIdEquals(productId));
    }
    if (metricId != null) {
      specification = specification.and(ContractEntity.metricIdEquals(metricId));
    }
    if (billingProvider != null) {
      specification = specification.and(ContractEntity.billingProviderEquals(billingProvider));
    }
    if (billingAccountId != null) {
      specification = specification.and(ContractEntity.billingAccountIdEquals(billingAccountId));
    }
    if (timestamp != null) {
      specification = specification.and(ContractEntity.activeOn(timestamp));
    }

    return contractRepository.getContracts(specification).stream()
        .map(mapper::contractEntityToDto)
        .toList();
  }

  /**
   * First look up an existing contract by UUID. Instead of truly updating this entity in the
   * database, create a copy of it with a new UUID and update values accordingly from the provided
   * dto. The original record will get an end date of "now", and the new record will become the
   * active contract by having its end date set to null.
   *
   * @param dto
   * @return Contract
   */
  @Transactional
  public Contract updateContract(Contract dto) {
    ContractEntity existingContract =
        contractRepository.findContract(UUID.fromString(dto.getUuid()));

    if (!isUpdateAllowed(existingContract, dto)) {

      log.warn(
          "Cannot update one or more of the attributes in the request.  Creating new contract record out of "
              + dto
              + "instead");

      return createContract(dto);
    }

    var now = OffsetDateTime.now();

    if (Objects.isNull(existingContract)) {
      var message =
          String.format(
              "Update called for contract uuid %s, but contract does not exist", dto.getUuid());
      log.error(message);
      throw new ContractMissingException(message);
    }

    // "sunset" the previous record
    existingContract.setEndDate(now);
    existingContract.setLastUpdated(now);
    existingContract.persist();

    // create new contract record representing an "update"
    ContractEntity newRecord = createContractForLogicalUpdate(dto);
    newRecord.persist();

    return dto;
  }

  /**
   * @param o
   * @param dto
   * @return boolean
   */
  boolean isUpdateAllowed(ContractEntity o, Contract dto) {

    return Objects.equals(o.getProductId(), dto.getProductId())
        && Objects.equals(o.getSubscriptionNumber(), dto.getSubscriptionNumber())
        && Objects.equals(o.getStartDate(), dto.getStartDate());
  }

  /**
   * Helper method that sets the UUID, start date, and end date fields that represent an update of
   * an existing contract
   *
   * @param dto
   * @return ContractEntity
   */
  public ContractEntity createContractForLogicalUpdate(Contract dto) {
    var newUuid = UUID.randomUUID();
    dto.setUuid(newUuid.toString());

    var now = OffsetDateTime.now();
    var newRecord = mapper.dtoToContractEntity(dto);

    newRecord.setStartDate(now);
    newRecord.setLastUpdated(now);
    newRecord.setEndDate(null);

    return newRecord;
  }

  /**
   * Delete a contract for a given uuid. This is hard delete, because its intended use is for
   * cleaning up test data.
   *
   * @param uuid
   */
  @Transactional
  public void deleteContract(String uuid) {

    var isSuccessful = contractRepository.deleteById(UUID.fromString(uuid));

    log.debug("Deletion status of {} is: {}", uuid, isSuccessful);
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
    } catch (ApiException e) {
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
        persistExistingContract(existingContract, now); // Persist previous contract

        persistContract(entity, now); // Persist new contract
        log.info("Previous contract archived and new contract created");
        statusResponse.setMessage("Previous contract archived and new contract created");
      }
    } else {
      // New contract
      var now = OffsetDateTime.now();
      persistContract(entity, now);
      statusResponse.setMessage("New contract created");
    }

    return statusResponse;
  }

  private boolean validPartnerEntitlementContract(PartnerEntitlementContract contract) {
    return Objects.nonNull(contract.getRedHatSubscriptionNumber())
        && Objects.nonNull(contract.getCurrentDimensions())
        && !contract.getCurrentDimensions().isEmpty()
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
        && Objects.nonNull(entity.getProductId());
  }

  private void persistContract(ContractEntity entity, OffsetDateTime now) {
    var uuid = UUID.randomUUID();
    entity.setUuid(uuid);
    entity.getMetrics().forEach(f -> f.setContractUuid(uuid));
    entity.setStartDate(now);
    entity.setLastUpdated(now);
    contractRepository.persist(entity);
    log.info("New contract created with UUID {}", uuid);
  }

  private Optional<ContractEntity> currentlyActiveContract(ContractEntity contract) {
    var specification =
        ContractEntity.subscriptionNumberEquals(contract.getSubscriptionNumber())
            .and(ContractEntity.isActive());
    return contractRepository.getContracts(specification).stream().findFirst();
  }

  private boolean isDuplicateContract(ContractEntity newEntity, ContractEntity existing) {
    return Objects.equals(newEntity, existing);
  }

  private void collectMissingUpStreamContractDetails(
      ContractEntity entity, PartnerEntitlementContract contract) throws ApiException {
    String awsCustomerAccountId = contract.getCloudIdentifiers().getAwsCustomerAccountId();
    String productCode = contract.getCloudIdentifiers().getProductCode();
    if (Objects.nonNull(contract.getCloudIdentifiers())
        && Objects.nonNull(awsCustomerAccountId)
        && Objects.nonNull(productCode)) {
      PageRequest page = new PageRequest();
      page.setSize(20);
      page.setNumber(0);
      log.trace(
          "Call Partner Api to fill missing information using customerAwsAccountId {} and vendorProductCode {}",
          awsCustomerAccountId,
          productCode);
      var result =
          partnerApi.getPartnerEntitlements(
              new QueryPartnerEntitlementV1()
                  .customerAwsAccountId(awsCustomerAccountId)
                  .vendorProductCode(productCode)
                  .page(page));
      mapUpstreamContractToContractEntity(entity, result);
    }
  }

  private void mapUpstreamContractToContractEntity(
      ContractEntity entity, PartnerEntitlements result) {
    if (Objects.nonNull(result.getContent())
        && !result.getContent().isEmpty()
        && Objects.nonNull(result.getContent().get(0))) {
      var entitlement = result.getContent().get(0);
      entity.setOrgId(entitlement.getRhAccountId());
      entity.setBillingProvider(
          ContractSourcePartnerEnum.getByCode(entitlement.getSourcePartner().value()));
      var partnerIdentity = entitlement.getPartnerIdentities();
      if (Objects.nonNull(partnerIdentity)) {
        entity.setBillingAccountId(partnerIdentity.getCustomerAwsAccountId());
      }
      mapRhEntitlementsToContractEntity(entity, entitlement);
    }
  }

  private void mapRhEntitlementsToContractEntity(
      ContractEntity entity, PartnerEntitlementV1 entitlement) {
    var rhEntitlements = entitlement.getRhEntitlements();
    if (Objects.nonNull(rhEntitlements)
        && !rhEntitlements.isEmpty()
        && Objects.nonNull(rhEntitlements.get(0))) {
      var sku = rhEntitlements.get(0).getSku();
      entity.setSku(sku);
      log.trace("Call swatch api to get producttags by sku {}", sku);
      try {
        OfferingProductTags productTags = syncResource.getSkuProductTags(sku);
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
