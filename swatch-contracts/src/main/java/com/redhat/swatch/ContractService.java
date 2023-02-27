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
package com.redhat.swatch;

import com.redhat.swatch.openapi.model.Contract;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContractService {

  private final ContractRepository contractRepository;
  private final ContractMapper mapper;

  ContractService(ContractRepository contractRepository, ContractMapper mapper) {
    this.contractRepository = contractRepository;
    this.mapper = mapper;
  }

  @Transactional
  com.redhat.swatch.openapi.model.Contract createContract(
      com.redhat.swatch.openapi.model.Contract contract) {

    var uuid = Objects.requireNonNullElse(contract.getUuid(), UUID.randomUUID().toString());
    contract.setUuid(uuid);

    var entity = mapper.dtoToContract(contract);

    entity.getMetrics().stream()
        .forEach(
            metric -> {
              metric.setContractUuid(entity.getUuid());
              metric.setContract(entity);
            });

    log.info("{}", entity);

    var now = OffsetDateTime.now();

    entity.setStartDate(now);
    entity.setLastUpdated(now);

    // Force end date to be null to indicate this it the current/applicable record
    entity.setEndDate(null);

    contractRepository.persist(entity);

    return contract;
  }

  public List<com.redhat.swatch.openapi.model.Contract> getContracts(
      Map<String, Object> parameters) {
    return contractRepository.getContracts(parameters).stream().map(mapper::contractToDto).toList();
  }

  @Transactional
  Contract updateContract(Contract dto) {

    com.redhat.swatch.Contract existingContract =
        contractRepository.findContract(UUID.fromString(dto.getUuid()));

    var now = OffsetDateTime.now();

    if (Objects.isNull(existingContract)) {
      log.warn(
          "Update called for contract uuid {}, but contract doesn't not exist.  Executing create contract instead",
          dto.getUuid());
      return createContract(dto);
    }

    var newData = mapper.dtoToContract(dto);
    newData
        .getMetrics()
        .forEach(
            metric -> {
              metric.setContractUuid(newData.getUuid());
              metric.setContract(newData);
            });

    /*
    If metric id, value, or product id changes, we want to keep record of the old value and logically update
     */
    var isNewRecordRequired = true; // NOSONAR

    if (isNewRecordRequired) {

      existingContract.setEndDate(now);
      existingContract.setLastUpdated(now);
      existingContract.persist();
      com.redhat.swatch.Contract newRecord = createContractForLogicalUpdate(dto);
      newRecord.persist();
    }
    return dto;
  }

  com.redhat.swatch.Contract createContractForLogicalUpdate(Contract dto) {
    var newUuid = UUID.randomUUID();
    var newRecord = mapper.dtoToContract(dto);
    newRecord.setUuid(newUuid);
    newRecord.setLastUpdated(OffsetDateTime.now());
    newRecord.setEndDate(null);
    newRecord.getMetrics().stream()
        .forEach(
            metric -> {
              metric.setContractUuid(newUuid);
              metric.setContract(newRecord);
            });
    return newRecord;
  }

  @Transactional
  void deleteContract(String uuid) {

    var isSuccessful = contractRepository.deleteById(UUID.fromString(uuid));

    log.debug("Deletion status of {} is: {}", uuid, isSuccessful);
  }
}
