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
import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.contract.exception.ContractMissingException;
import com.redhat.swatch.contract.exception.CreateContractException;
import com.redhat.swatch.contract.model.ContractMapper;
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
      String billingAccountId) {

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
    ContractEntity entity;
    try {
      // Fill up information from upstream and swatch
      entity = mapper.partnerContractToContractEntity(contract);
      collectMissingUpStreamContractDetails(entity, contract);
      if (!isValidEntity(entity)) {
        statusResponse.setMessage("Empty value in non-null fields");
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
        statusResponse.setMessage("Duplicate record found");
      } else {
        // Record found in contract table but, the contract has changed
        var now = OffsetDateTime.now();
        persistExistingContract(existingContract, now); // Persist previous contract

        persistContract(entity, now); // Persist new contract
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

  private void persistExistingContract(ContractEntity existingContract, OffsetDateTime now) {
    existingContract.setEndDate(now);
    existingContract.setLastUpdated(now);
    contractRepository.persist(existingContract);
  }

  private boolean isValidEntity(ContractEntity entity) {
    // Check all non-null fields
    return !Objects.isNull(entity)
        && !Objects.isNull(entity.getSubscriptionNumber())
        && !Objects.isNull(entity.getOrgId())
        && !Objects.isNull(entity.getSku())
        && !Objects.isNull(entity.getBillingProvider())
        && !Objects.isNull(entity.getBillingAccountId())
        && !Objects.isNull(entity.getProductId());
  }

  private void persistContract(ContractEntity entity, OffsetDateTime now) {
    var uuid = UUID.randomUUID();
    entity.setUuid(uuid);
    entity.getMetrics().forEach(f -> f.setContractUuid(uuid));
    entity.setStartDate(now);
    entity.setLastUpdated(now);
    contractRepository.persist(entity);
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

  // SWATCH-1014 reformat this logic
  private void collectMissingUpStreamContractDetails( // NOSONAR
      ContractEntity entity, PartnerEntitlementContract contract) throws ApiException {
    PageRequest page = new PageRequest();
    page.setSize(20);
    page.setNumber(0);
    if (Objects.nonNull(contract.getCloudIdentifiers())
        && Objects.nonNull(contract.getCloudIdentifiers().getAwsCustomerId())) {
      var result =
          partnerApi.getPartnerEntitlements(
              new QueryPartnerEntitlementV1()
                  .customerAwsAccountId(contract.getCloudIdentifiers().getAwsCustomerId())
                  .page(page));
      if (Objects.nonNull(result.getContent()) && Objects.nonNull(result.getContent().get(0))) {
        var entitlement = result.getContent().get(0);
        entity.setOrgId(entitlement.getRhAccountId());
        entity.setBillingProvider(entitlement.getSourcePartner().value());
        var partnerIdentity = entitlement.getPartnerIdentities();
        if (Objects.nonNull(partnerIdentity)) {
          entity.setBillingAccountId(partnerIdentity.getCustomerAwsAccountId());
        }
        var rhEntitlements = entitlement.getRhEntitlements();
        if (Objects.nonNull(rhEntitlements)
            && !rhEntitlements.isEmpty()
            && Objects.nonNull(rhEntitlements.get(0))) {
          var sku = rhEntitlements.get(0).getSku();
          entity.setSku(sku);
          OfferingProductTags productTags = syncResource.getSkuProductTags(sku);
          if (Objects.nonNull(productTags.getData())
              && Objects.nonNull(productTags.getData().get(0))) {
            entity.setProductId(productTags.getData().get(0));
          } else {
            log.error("Error getting product tags");
          }
        }
      }
    }
  }
}
