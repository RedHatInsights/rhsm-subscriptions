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

import com.redhat.swatch.clients.rh.partner.gateway.api.model.QueryPartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.ApiException;
import com.redhat.swatch.clients.rh.partner.gateway.api.resources.PartnerApi;
import com.redhat.swatch.contract.model.ContractMapper;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@ApplicationScoped
public class ContractService {

  private final ContractRepository contractRepository;
  private final ContractMapper mapper;

  // private final PartnerContractMapper partnerMapper;

  @Inject @RestClient PartnerApi partnerApi;

  ContractService(ContractRepository contractRepository, ContractMapper mapper
      /*PartnerContractMapper partnerMapper*/ ) {
    this.contractRepository = contractRepository;
    this.mapper = mapper;
    // this.partnerMapper = partnerMapper;
  }

  @Transactional
  public Contract createContract(Contract contract) {

    var uuid = Objects.requireNonNullElse(contract.getUuid(), UUID.randomUUID().toString());
    contract.setUuid(uuid);

    var entity = mapper.dtoToContractEntity(contract);

    var now = OffsetDateTime.now();

    entity.setStartDate(now);
    entity.setLastUpdated(now);

    // Force end date to be null to indicate this it the current/applicable record
    entity.setEndDate(null);

    contractRepository.persist(entity);

    return contract;
  }

  public List<Contract> getContracts(Map<String, Object> parameters) {
    return contractRepository.getContracts(parameters).stream()
        .map(mapper::contractEntityToDto)
        .toList();
  }

  @Transactional
  public Contract updateContract(Contract dto) {

    ContractEntity existingContract =
        contractRepository.findContract(UUID.fromString(dto.getUuid()));

    var now = OffsetDateTime.now();

    if (Objects.isNull(existingContract)) {
      log.warn(
          "Update called for contract uuid {}, but contract doesn't not exist.  Executing create contract instead",
          dto.getUuid());
      return createContract(dto);
    }

    /*
    If metric id, value, or product id changes, we want to keep record of the old value and logically update
     */
    var isNewRecordRequired = true;

    if (isNewRecordRequired) { // NOSONAR

      existingContract.setEndDate(now);
      existingContract.setLastUpdated(now);
      existingContract.persist();
      ContractEntity newRecord = createContractForLogicalUpdate(dto);
      newRecord.persist();
    }
    return dto;
  }

  public ContractEntity createContractForLogicalUpdate(Contract dto) {
    var newUuid = UUID.randomUUID();
    var newRecord = mapper.dtoToContractEntity(dto);
    newRecord.setUuid(newUuid);
    newRecord.setLastUpdated(OffsetDateTime.now());
    newRecord.setEndDate(null);

    return newRecord;
  }

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
      entity = mapper.reconcileUpstreamContract(contract);
      collectMissingUpStreamContractDetails(entity, contract);
    } catch (Exception e) {
      log.debug(e.getMessage());
      statusResponse.setMessage("An Error occurred while reconciling contract");
      return statusResponse;
    }

    // contractRepository.findContract
    if (isDuplicateContract(entity, entity)) {
      statusResponse.setMessage("Duplicate record found");
      return statusResponse;
    }

    if (Objects.nonNull(entity)) {
      createContract(entity);
      statusResponse.setMessage("Contract created");
    } else {
      statusResponse.setMessage("Empty entity passed");
    }
    return statusResponse;
  }

  private boolean isDuplicateContract(ContractEntity newEntity, ContractEntity existing) {

    return false;
  }

  private void createContract(ContractEntity entity) {
    entity.setUuid(UUID.randomUUID());
    var now = OffsetDateTime.now();

    entity.setStartDate(now);
    entity.setLastUpdated(now);
    contractRepository.persist(entity);
  }

  private void collectMissingUpStreamContractDetails(
      ContractEntity entity, PartnerEntitlementContract contract) {
    try {
      if (Objects.nonNull(contract.getCloudIdentifiers())
          && Objects.nonNull(contract.getCloudIdentifiers().getAwsCustomerId())) {
        var result =
            partnerApi.getPartnerEntitlements(
                new QueryPartnerEntitlementV1()
                    .customerAwsAccountId(contract.getCloudIdentifiers().getAwsCustomerId()));
        var partnerEntitlements = result.getPartnerEntitlements();
        var entitlement = partnerEntitlements.get(0);
        if (Objects.nonNull(entitlement)) {
          entity.setOrgId(entitlement.getRhAccountId());
          var purchase = entitlement.getPurchase();
        }
      }
    } catch (ApiException e) {
      throw new RuntimeException(e);
    }
  }
}
