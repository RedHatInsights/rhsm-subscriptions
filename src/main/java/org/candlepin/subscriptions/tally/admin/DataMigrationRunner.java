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
package org.candlepin.subscriptions.tally.admin;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataMigrationRunner {
  private final ExecutorService executor;
  private final JdbcTemplate jdbcTemplate;
  private final MeterRegistry meterRegistry;

  @Autowired
  public DataMigrationRunner(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
    executor = Executors.newFixedThreadPool(4);
    this.jdbcTemplate = jdbcTemplate;
    this.meterRegistry = meterRegistry;
  }

  @PreDestroy
  protected void destroy() throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
      log.warn("Data migration not yet terminated in 20 seconds.");
    }
  }

  public void migrate(
      Class<? extends DataMigration> migrationClass, String startingRecordID, int batchSize) {
    executor.execute(
        () -> {
          DataMigration dataMigration;
          try {
            dataMigration = DataMigration.getMigration(migrationClass, jdbcTemplate, meterRegistry);
          } catch (Exception e) {
            log.warn("Unable to constructor migration from class {}", migrationClass);
            return;
          }

          String lastProcessedId = startingRecordID;
          do {
            SqlRowSet page = dataMigration.extract(lastProcessedId, batchSize);
            lastProcessedId = dataMigration.transformAndLoad(page);
          } while (lastProcessedId != null);

          dataMigration.recordCompleted();
        });
  }
}
