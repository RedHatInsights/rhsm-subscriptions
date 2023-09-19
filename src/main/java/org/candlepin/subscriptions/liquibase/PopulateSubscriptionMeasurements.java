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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.RollbackImpossibleException;

/**
 * This class is responsible for moving the various cores/sockets columns from subscription_capacity
 * to subscription_measurements.
 */
public class PopulateSubscriptionMeasurements extends LiquibaseCustomTask {
  private static final String SOCKETS = "SOCKETS";
  private static final String CORES = "CORES";

  public static final String CAPACITY = "subscription_capacity sc";
  /* Join against subscription to avoid FK errors.  Capacity records without a subscription will
   * not be migrated.  In Stage, however, we do have capacity records with subscription IDs and
   * start times that match subscription records but with differing org_ids.  These are dropped too.
   */
  public static final String SUBSCRIPTION_ID_JOIN =
      " JOIN subscription s ON sc.subscription_id = s.subscription_id AND sc.org_id = s.org_id AND sc.begin_date = s.start_date";

  @Override
  public void executeTask(Database database) throws DatabaseException, SQLException {

    int total = 0;

    ResultSet socketsSet =
        executeQuery(
            "SELECT DISTINCT sc.subscription_id, sc.begin_date, sockets FROM "
                + CAPACITY
                + SUBSCRIPTION_ID_JOIN
                + " WHERE sockets IS NOT NULL AND sockets > 0");
    total += insertRows(SOCKETS, "PHYSICAL", socketsSet);

    ResultSet hypervisorSocketsSet =
        executeQuery(
            "SELECT DISTINCT sc.subscription_id, sc.begin_date, hypervisor_sockets FROM "
                + CAPACITY
                + SUBSCRIPTION_ID_JOIN
                + " WHERE hypervisor_sockets IS NOT NULL AND hypervisor_sockets > 0");
    total += insertRows(SOCKETS, "HYPERVISOR", hypervisorSocketsSet);

    ResultSet coresSet =
        executeQuery(
            "SELECT DISTINCT sc.subscription_id, sc.begin_date, cores FROM "
                + CAPACITY
                + SUBSCRIPTION_ID_JOIN
                + " WHERE cores IS NOT NULL AND cores > 0");

    total += insertRows(CORES, "PHYSICAL", coresSet);
    ResultSet hypervisorCoresSet =
        executeQuery(
            "SELECT DISTINCT sc.subscription_id, sc.begin_date, hypervisor_cores FROM "
                + CAPACITY
                + SUBSCRIPTION_ID_JOIN
                + " WHERE hypervisor_cores IS NOT NULL AND hypervisor_cores > 0");

    total += insertRows(CORES, "HYPERVISOR", hypervisorCoresSet);

    logger.info(total + " rows inserted into the subscription_measurements table");
  }

  public int insertRows(String metricId, String measurementType, ResultSet resultSet)
      throws DatabaseException, SQLException {
    int total = 0;
    String insertStatement =
        "INSERT INTO subscription_measurements (subscription_id, start_date, metric_id, "
            + "measurement_type, value) VALUES (?, ?, ?, ?, ?)";
    try (resultSet) {
      while (resultSet.next()) {
        String subscriptionId = resultSet.getString("subscription_id");
        Timestamp startDate = resultSet.getTimestamp("begin_date");
        int value = resultSet.getInt(3);
        total +=
            executeUpdate(
                insertStatement, subscriptionId, startDate, metricId, measurementType, value);
      }

      return total;
    }
  }

  @Override
  public boolean disableAutoCommit() {
    return true;
  }

  @Override
  public String getConfirmationMessage() {
    return "Migration of subscription_capacity core and sockets data to subscription_measurements "
        + "complete";
  }

  @Override
  public void rollback(Database database)
      throws CustomChangeException, RollbackImpossibleException {
    try (PreparedStatement truncate = prepareStatement("TRUNCATE subscription_measurements")) {
      truncate.executeUpdate();
      connection.commit();
    } catch (DatabaseException | SQLException e) {
      throw new RollbackImpossibleException("Could not rollback: " + e.getMessage(), e);
    }
  }
}
