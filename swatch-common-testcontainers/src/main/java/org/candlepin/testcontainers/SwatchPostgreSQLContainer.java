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
package org.candlepin.testcontainers;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import org.candlepin.testcontainers.exceptions.ExecuteStatementInContainerException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EqualsAndHashCode(callSuper = true)
public class SwatchPostgreSQLContainer extends PostgreSQLContainer<SwatchPostgreSQLContainer> {
  private static final String POSTGRESQL_IMAGE = "quay.io/centos7/postgresql-13-centos7";

  public SwatchPostgreSQLContainer(String database) {
    super(DockerImageName.parse(POSTGRESQL_IMAGE).asCompatibleSubstituteFor("postgres"));
    waitingFor(Wait.forLogMessage(".*Starting server.*", 1));
    setCommand("run-postgresql");

    withDatabaseName(database);
    withUsername(database);
    withPassword(database);
    withNetworkAliases(UUID.randomUUID().toString());
    // SMELL: Workaround for https://github.com/testcontainers/testcontainers-java/issues/7539
    // This is because testcontainers randomly fails to start a container when using Podman socket.
    withStartupAttempts(3);
  }

  public void deleteAllRows(String table) {
    executeStatement("DELETE FROM " + table);
  }

  public void insertRow(String table, String[] columns, String[] values) {
    executeStatement(
        "INSERT INTO "
            + table
            + "("
            + String.join(",", columns)
            + ") "
            + "VALUES ("
            + arrayToString(values)
            + ")");
  }

  @Override
  protected void configure() {
    super.configure();
    addEnv("POSTGRESQL_USER", getUsername());
    addEnv("POSTGRESQL_PASSWORD", getPassword());
    addEnv("POSTGRESQL_DATABASE", getDatabaseName());
  }

  private void executeStatement(String statement) {
    try {
      ExecResult result =
          execInContainer(
              "psql",
              "-h",
              "localhost",
              "-U",
              getUsername(),
              "-d",
              getDatabaseName(),
              "-c",
              statement + ";");
      if (result.getExitCode() != 0) {
        throw new ExecuteStatementInContainerException(
            "Fail to execute statement " + statement + ". Output: " + result.getStderr());
      }
    } catch (IOException e) {
      throw new ExecuteStatementInContainerException(
          "Fail to execute statement in " + getContainerId(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExecuteStatementInContainerException(
          "Fail to execute statement in " + getContainerId(), e);
    }
  }

  private String arrayToString(String[] array) {
    return Stream.of(array).map(v -> "'" + v + "'").collect(Collectors.joining(","));
  }
}
