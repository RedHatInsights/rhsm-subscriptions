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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;

/**
 * This class is responsible for moving the various cores/instance_count/sockets columns over to a
 * separate table to better normalize the database.
 */
public class MigrateTallyColumnsTask extends LiquibaseCustomTask {

  @Override
  public void executeTask(Database database) throws DatabaseException, SQLException {

    int total = 0;
    ResultSet totalTuple =
        this.executeQuery(
            "SELECT id, cores, instance_count, sockets FROM tally_snapshots "
                + "WHERE cores IS NOT NULL OR instance_count IS NOT NULL OR sockets IS NOT NULL");
    total += insertRows("TOTAL", totalTuple);

    ResultSet physicalTuple =
        this.executeQuery(
            "SELECT id, physical_cores as cores, physical_instance_count as instance_count, "
                + "physical_sockets as sockets FROM tally_snapshots WHERE physical_cores IS NOT NULL "
                + "OR physical_instance_count IS NOT NULL OR physical_sockets IS NOT NULL");
    total += insertRows("PHYSICAL", physicalTuple);

    ResultSet hypervisorTuple =
        this.executeQuery(
            "SELECT id, hypervisor_cores as cores, hypervisor_instance_count as instance_count, "
                + "hypervisor_sockets as sockets FROM tally_snapshots WHERE hypervisor_cores IS NOT NULL "
                + "OR hypervisor_instance_count IS NOT NULL OR hypervisor_sockets IS NOT NULL");
    total += insertRows("HYPERVISOR", hypervisorTuple);

    this.logger.info(total + " rows inserted into the hardware_measurements table");
  }

  public int insertRows(String discriminator, ResultSet resultSet)
      throws DatabaseException, SQLException {
    int total = 0;
    String insertStatement =
        "INSERT INTO hardware_measurements (snapshot_id, measurement_type, "
            + "cores, instance_count, sockets) VALUES (?, ?, ?, ?, ?)";
    try {
      while (resultSet.next()) {
        int cores = resultSet.getInt("cores");
        int instanceCount = resultSet.getInt("instance_count");
        int sockets = resultSet.getInt("sockets");
        UUID snapshotId = resultSet.getObject("id", UUID.class);
        total +=
            executeUpdate(
                insertStatement, snapshotId, discriminator, cores, instanceCount, sockets);
      }

      return total;
    } finally {
      resultSet.close();
    }
  }

  @Override
  public boolean disableAutoCommit() {
    return true;
  }

  @Override
  public String getConfirmationMessage() {
    return "Migration of tally_snapshots core/sockets/instance_count columns complete";
  }
}
