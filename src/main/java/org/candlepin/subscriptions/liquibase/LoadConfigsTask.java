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
package org.candlepin.subscriptions.liquibase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.stream.Stream;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.logging.LogService;
import liquibase.logging.Logger;

/**
 * Custom liquibase task for loading org_config and account_config files from text files.
 *
 * <p>Reads two environment variables (SWATCH_ACCOUNT_CONFIG_PATH and SWATCH_ORG_CONFIG_PATH) to get
 * the path to
 */
public class LoadConfigsTask extends LiquibaseCustomTask {

  private static final String ORG_CONFIG_SQL =
      "insert into org_config (org_id, sync_enabled, opt_in_type, created, updated) values "
          + "(?, true, 'DB', now(), now()) on conflict do nothing";

  private static final String ACCOUNT_CONFIG_SQL =
      "insert into account_config "
          + "(account_number, sync_enabled, reporting_enabled, opt_in_type, created, updated) values "
          + "(?, true, true, 'DB', now(), now()) on conflict do nothing";

  @Override
  public void executeTask(Database database) {
    populateOrgConfig();
    populateAccountConfig();
  }

  private void populateAccountConfig() {
    String accountConfigPath = System.getenv("SWATCH_ACCOUNT_CONFIG_PATH");
    if (accountConfigPath != null) {
      getLogger().info("Updating account_config");
      try (Stream<String> lines = Files.lines(Paths.get(accountConfigPath))) {
        Integer rowsModified =
            lines
                .filter(s -> !s.isEmpty() && !s.startsWith("{"))
                .map(this::addAccountToConfig)
                .reduce(0, Integer::sum);
        getLogger().info("Added " + rowsModified + " entries to account_config");
      } catch (IOException e) {
        throw new ConfigUpdateException(e);
      }
    } else {
      getLogger()
          .debug("SWATCH_ACCOUNT_CONFIG_PATH not defined. account_config will not be updated.");
    }
  }

  private int addAccountToConfig(String account) {
    try {
      return executeUpdate(ACCOUNT_CONFIG_SQL, account);
    } catch (DatabaseException | SQLException e) {
      throw new ConfigUpdateException(e);
    }
  }

  private void populateOrgConfig() {
    String orgConfigPath = System.getenv("SWATCH_ORG_CONFIG_PATH");
    if (orgConfigPath != null) {
      getLogger().info("Updating org_config");
      try (Stream<String> lines = Files.lines(Paths.get(orgConfigPath))) {
        Integer rowsModified =
            lines
                .filter(s -> !s.isEmpty() && !s.startsWith("Candlepin") && !s.startsWith("{"))
                .map(this::addOrgToConfig)
                .reduce(0, Integer::sum);
        getLogger().info("Added " + rowsModified + " entries to org_config");
      } catch (IOException e) {
        throw new ConfigUpdateException(e);
      }
    } else {
      getLogger().debug("SWATCH_ORG_CONFIG_PATH not defined. org_config will not be updated.");
    }
  }

  private int addOrgToConfig(String orgId) {
    try {
      return executeUpdate(ORG_CONFIG_SQL, orgId);
    } catch (DatabaseException | SQLException e) {
      throw new ConfigUpdateException(e);
    }
  }

  @Override
  public boolean disableAutoCommit() {
    return false;
  }

  @Override
  public Logger getLogger() {
    return LogService.getLog(LoadConfigsTask.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Loading of org/account configs complete";
  }
}
