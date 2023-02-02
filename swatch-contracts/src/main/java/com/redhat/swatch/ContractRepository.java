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

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContractRepository implements PanacheRepositoryBase<Contract, UUID> {

  public List<Contract> getContracts(Map<String, Object> parameters) {
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

    String query =
        nonNullParams.entrySet().stream()
            .map(entry -> entry.getKey() + "=:" + entry.getKey())
            .collect(Collectors.joining(" and "));

    // TODO make this less ridiculous

    if (nonNullParams.containsKey("metricId")) {
      query =
          "select c from Contract c inner join ContractMetric m on c.uuid = m.contractUuid where "
              + query;
      query = query.replace("metricId=:", "m.metricId=:");
    }

    log.info("Dynamically generated query: {}", query);

    return find(query, nonNullParams).list();
  }

  Contract findContract(UUID uuid) {
    log.info("bananas");
    return find("uuid", uuid).firstResult();
  }
}
