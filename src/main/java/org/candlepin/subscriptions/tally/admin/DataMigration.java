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
import java.lang.reflect.InvocationTargetException;
import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

public abstract class DataMigration {
  private static final String LIQUIBASE_CHANGELOG_INSERT =
      "insert into databasechangelog(id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id)\n"
          + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

  private static final String MAX_ORDEREXECUTED_QUERY =
      "select max(orderexecuted) from databasechangelog";

  private static final String DELETE_CHANGELOG_QUERY = "delete from databasechangelog where id=?";

  protected JdbcTemplate jdbcTemplate;

  protected MeterRegistry meterRegistry;

  protected DataMigration(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
    this.jdbcTemplate = jdbcTemplate;
    this.meterRegistry = meterRegistry;
  }

  // Can we stream these back
  public abstract SqlRowSet extract(String recordOffset, int batchSize);

  public abstract String transformAndLoad(SqlRowSet data);

  public abstract void recordCompleted();

  protected void markLiquibaseChangesetRan(JdbcTemplate jdbcTemplate, Map<String, Object> values) {
    int maxOrderExecutedValue =
        Optional.ofNullable(jdbcTemplate.queryForObject(MAX_ORDEREXECUTED_QUERY, Integer.class))
            .orElse(0);
    jdbcTemplate.update(DELETE_CHANGELOG_QUERY, values.get("id"));
    jdbcTemplate.update(
        LIQUIBASE_CHANGELOG_INSERT,
        values.get("id"),
        values.get("author"),
        values.get("filename"),
        values.getOrDefault("dateexecuted", Date.valueOf(ZonedDateTime.now().toLocalDate())),
        values.getOrDefault("orderexecuted", maxOrderExecutedValue + 1),
        values.getOrDefault("exectype", "MARK_RAN"),
        values.get("md5sum"),
        values.getOrDefault("description", "customChange"),
        values.getOrDefault(
            "comments", String.format("Manually migrated via %s", this.getClass().getName())),
        values.get("tag"), // nullable
        values.getOrDefault("liquibase", "4.9.1"),
        values.get("contexts"), // nullable
        values.get("labels"), // nullable
        values.get("deployment_id")); // nullable
  }

  static DataMigration getMigration(
      Class<? extends DataMigration> clazz, JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry)
      throws NoSuchMethodException,
          InvocationTargetException,
          InstantiationException,
          IllegalAccessException {
    return clazz
        .getConstructor(JdbcTemplate.class, MeterRegistry.class)
        .newInstance(jdbcTemplate, meterRegistry);
  }
}
