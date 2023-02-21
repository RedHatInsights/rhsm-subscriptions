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
  com.redhat.swatch.openapi.model.Contract saveContract(
      com.redhat.swatch.openapi.model.Contract contract) {

    // temporary...forcing new UUID to work on the create case
    contract.setUuid(null);
    //    entity.setUuid(null);
    var uuid = Objects.requireNonNullElse(contract.getUuid(), UUID.randomUUID().toString());
    contract.setUuid(uuid);

    var entity = mapper.dtoToContract(contract);

    log.info("{}", entity);

    var now = OffsetDateTime.now();

    entity.setStartDate(now);
    entity.setLastUpdated(now);

    contractRepository.persist(entity);

    return contract;
  }

  public List<com.redhat.swatch.openapi.model.Contract> getContracts(
      Map<String, Object> parameters) {
    return contractRepository.getContracts(parameters).stream()
        .map(mapper::contractToDto)
        .toList();
  }

  @Transactional
  void updateContract(Contract dto) {

    log.info("{}", dto);

    /*
    - should update all fields with the vlaues from the payload
    - if an update to dates, metric id, or product id we should be creating new Contract/ContractMetric records,
    and updating the existing one with end_date = Date.now()
     */

    com.redhat.swatch.Contract existingContract =
        contractRepository.findContract(UUID.fromString(dto.getUuid()));

    var newContractRecord = new com.redhat.swatch.Contract(existingContract);

    var mapped = mapper.dtoToContract(dto);

    var now = OffsetDateTime.now();
    existingContract.setEndDate(now);



  }

  @Transactional
  void deleteContract(String uuid) {
    contractRepository.deleteById(UUID.fromString(uuid));
  }
}
