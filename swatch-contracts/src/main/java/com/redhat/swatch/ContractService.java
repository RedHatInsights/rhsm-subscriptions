package com.redhat.swatch;

import com.redhat.swatch.openapi.model.BillingProvider;
import com.redhat.swatch.openapi.model.Contract;
import com.redhat.swatch.openapi.model.Metric;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;

@Slf4j
@ApplicationScoped
public class ContractService {

  @Inject
  ContractRepository repository;

  @SneakyThrows
  List<Contract> saveContract(Contract contract) {

    //strip uuid for now because mismatch
    contract.setUuid(null);

    var records = new ArrayList<ContractsEntity>();

    //stupid things because lists
    for (Metric x : contract.getMetrics()) {
      var entity = new ContractsEntity();

      BeanUtils.copyProperties(entity, contract);

      entity.setUuid(UUID.randomUUID());
      var now = OffsetDateTime.now();
      entity.setStartDate(now);
      entity.setLastUpdated(now);

      //TODO these aren't part of the api schema
      entity.setSku("BANANAS");

      entity.setMetricId(x.getMetricId());
      entity.setValue(x.getValue().doubleValue());

      records.add(entity);
    }

    repository.persist(records);

    return null;
  }

  @SneakyThrows
  public List<Contract> getAllContracts() {

    //need to add filtering here

    List<ContractsEntity> contracts = repository.listAll();

    var dto = new Contract();

    if (!contracts.isEmpty()) {

      //Since everything will be the same except for the metric id & value
      var x = contracts.get(0);

      dto.setUuid(x.getUuid().toString());
      dto.setBillingProvider(BillingProvider.fromValue(x.getBillingProvider()));
      dto.setEndDate(x.getEndDate());
      dto.setOrgId(x.getOrgId());
      dto.setBillingAccountId(x.getBillingAccountId());
      dto.setStartDate(x.getStartDate());
      dto.setSubscriptionNumber(x.getSubscriptionNumber());
      dto.setProductId(x.getProductId());

      List<Metric> metrics = contracts.stream().map(y -> {
        var metric = new Metric();
        metric.setMetricId(y.getMetricId());
        metric.setValue(BigDecimal.valueOf(y.getValue()));
        return metric;
      }).collect(Collectors.toList());

      dto.setMetrics(metrics);

    }

    Function<ContractsEntity, List<Object>> compositeKey = contractRecord ->
        Arrays.<Object>asList(contractRecord.getUU, personRecord.getAge());

    return null;
  }
}