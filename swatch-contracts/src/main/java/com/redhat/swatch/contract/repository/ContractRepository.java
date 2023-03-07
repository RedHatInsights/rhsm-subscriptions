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
package com.redhat.swatch.contract.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContractRepository implements PanacheRepositoryBase<ContractEntity, UUID> {


  public List<ContractEntity> getContracts(Map<String, Object> parameters, boolean isCurrentlyActive) {
    if (parameters == null) {
      return listAll();
    }

    Map<String, Object> nonNullParams =
        parameters.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (nonNullParams.isEmpty()) {
      return listAll();
    }

    var propertiesRequiringJoin = List.of("metricId");
    var isJoinTableNeeded = nonNullParams.containsKey("metricId");

    String query =
        nonNullParams.keySet().stream()
            .map(
                key -> {
                  if (isJoinTableNeeded) {
                    if (propertiesRequiringJoin.contains(key)) {
                      return "m." + key + "=:" + key;
                    } else {
                      return "c." + key + "=:" + key;
                    }
                  }

                  return key + "=:" + key;
                })
            .collect(Collectors.joining(" and "));

    if(isCurrentlyActive){
      //TODO take into consideration m. or c.
      query += " and endDate IS NULL ";
    }



    if (isJoinTableNeeded) {
      var metricTableJoin =
          "select c from "
              + ContractEntity.class.getName()
              + " c inner join "
              + ContractMetricEntity.class.getName()
              + " m on c.uuid = m.contractUuid where ";
      query = metricTableJoin + query;
    }

    log.info("Dynamically generated query: {}", query);

    return find(query, nonNullParams).list();
  }

  public Optional<ContractEntity> getContract(
      Map<String, Object> parameters, boolean isCurrentlyActive) {
    if (parameters == null) {
      return Optional.empty();
    }

    Map<String, Object> nonNullParams =
        parameters.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (nonNullParams.isEmpty()) {
      return Optional.empty();
    }

    String query =
        nonNullParams.keySet().stream()
            .map(key -> key + "=:" + key)
            .collect(Collectors.joining(" and "));

    if (isCurrentlyActive) {
      query += " and endDate IS NULL ";
    }

    var contractTable = "select c from " + ContractEntity.class.getName() + " c where ";
    query = contractTable + query;

    log.info("Dynamically generated query: {}", query);

    return find(query, nonNullParams).singleResultOptional();
  }

  public ContractEntity findContract(UUID uuid) {
    log.info("Find contract by uuid {}", uuid);
    return find("uuid", uuid).firstResult();
  }
}
