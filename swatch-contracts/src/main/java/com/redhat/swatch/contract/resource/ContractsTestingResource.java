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
package com.redhat.swatch.contract.resource;

import com.redhat.swatch.JmsPriceProducer;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.openapi.resource.ApiException;
import com.redhat.swatch.contract.openapi.resource.DefaultApi;
import com.redhat.swatch.contract.service.ContractService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.transaction.Transactional;
import javax.ws.rs.ProcessingException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContractsTestingResource implements DefaultApi {

  @Inject ContractService service;

  @Inject JmsPriceProducer producer;

  /*@Inject
  @Channel("prices")
  Emitter<String> emitter;
  @Transactional
  public void testActiveMQ(){
    producer.generate();
    emitter.send("Hello Kartik producer");
    Jsonb jsonb = JsonbBuilder.create();
    String jsonString = jsonb.toJson(contract);
    producer.sendContract(jsonString);
  }*/

  @Override
  @Transactional
  public Contract createContract(Contract contract) throws ApiException, ProcessingException {

    return service.createContract(contract);
  }

  /**
   * @param contract
   * @return
   * @throws ApiException
   * @throws ProcessingException
   */

  public StatusResponse createPartnerEntitlementContract(PartnerEntitlementContract contract)
      throws ApiException, ProcessingException {
    Jsonb jsonb = JsonbBuilder.create();
    String jsonString = jsonb.toJson(contract);
    producer.sendContract(jsonString);
    return null;
  }

  @Override
  public void deleteContractByUUID(String uuid) throws ApiException, ProcessingException {

    service.deleteContract(uuid);
  }

  @Override
  public List<Contract> getContract(
      String orgId,
      String productId,
      String metricId,
      String billingProvider,
      String billingAccountId)
      throws ApiException, ProcessingException {

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("orgId", orgId);
    parameters.put("productId", productId);
    parameters.put("metricId", metricId);
    parameters.put("billingProvider", billingProvider);
    parameters.put("billingAccountId", billingAccountId);

    return service.getContracts(parameters);
  }

  @Override
  public Contract updateContract(String uuid, Contract contract)
      throws ApiException, ProcessingException {

    return service.updateContract(contract);
  }
}
