package org.candlepin.subscriptions.tally.admin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DataMigrationRunner {


  @Autowired
  JdbcTemplate jdbcTemplate;

  public void sanityCheck() {
    log.info("Bananas");
    migrateCoresSocketsData();

    var threadPoolSize = 4;

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadPoolSize);

    migrateCoresSocketsData().forEach(sqlStatement -> {
      executor.submit(() -> {

        log.info("Running sql: {}", sqlStatement);
        jdbcTemplate.update(sqlStatement);
        updateDatabaseChangelog("placeholder for now....we need to shove a comment in somewhere");
        return null;

      });
    });
  }

  // src/main/resources/liquibase/202208251616-migrate-cores-sockets-data.xml
  public List<String> migrateCoresSocketsData() {

    return Arrays.asList(
        // 202208251616-1
        "insert into instance_measurements(instance_id, uom, value) select id, 'CORES', cores from hosts where cores is not null on conflict(instance_id, uom) do update set value =excluded.value;"
        // 202208251616-2
        ,
        "insert into instance_measurements(instance_id, uom, value) select id, 'SOCKETS', sockets from hosts where sockets is not null on conflict(instance_id, uom) do update set value=excluded.value;"
        // 202208251616-3
        ,
        "insert into tally_measurements(snapshot_id, measurement_type, uom, value) select snapshot_id, measurement_type, 'SOCKETS', sockets from hardware_measurements where sockets is not null on conflict(snapshot_id, measurement_type, uom) do update set value=excluded.value;"
        // 202208251616-4
        ,
        "insert into tally_measurements(snapshot_id, measurement_type, uom, value) select snapshot_id, measurement_type, 'CORES', cores from hardware_measurements where cores is not null on conflict(snapshot_id, measurement_type, uom) do update set value=excluded.value;"
        // 202208251616-5
        ,
        "insert into tally_measurements(snapshot_id, measurement_type, uom, value) select snapshot_id, measurement_type, 'INSTANCES', instance_count from hardware_measurements where instance_count is not null on conflict(snapshot_id, measurement_type, uom) do update set value=excluded.value;"

    );


  }



  public void updateDatabaseChangelog(String comment) {

    // TODO
    log.info("Entry into databasechangelog", comment);
  }



  // {
  // "id": "202209301614-1",
  // "author": "mstead",
  // "filename": "liquibase/202209301614-migrate-org-id-to-events-table.xml",
  // "dateexecuted": "2022-10-12 18:03:48.245371",
  // "orderexecuted": 143,
  // "exectype": "EXECUTED",
  // "md5sum": "8:ba2f1a325928e9bb87387bd97e27f2bc",
  // "description": "sql",
  // "comments": "Migrate org_id to events table from account_config.",
  // "tag": null,
  // "liquibase": "4.9.1",
  // "contexts": null,
  // "labels": null,
  // "deployment_id": "5597795748"
  // }


}

