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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

/** testcontainers override to set centos postgres compatible env vars */
public class CentosPostgreSQLContainer extends PostgreSQLContainer {
  CentosPostgreSQLContainer() {
    super(
        DockerImageName.parse("quay.io/centos7/postgresql-12-centos7")
            .asCompatibleSubstituteFor("postgres"));
    this.waitStrategy =
        new LogMessageWaitStrategy()
            .withRegEx(".*listening on IPv4 address.*")
            .withTimes(1)
            .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS));
    this.setCommand("run-postgresql");
  }

  @Override
  protected void configure() {
    super.configure();
    addEnv("POSTGRESQL_USER", getUsername());
    addEnv("POSTGRESQL_PASSWORD", getPassword());
    addEnv("POSTGRESQL_DATABASE", getDatabaseName());
  }

  @Override
  protected void waitUntilContainerStarted() {
    this.waitStrategy.waitUntilReady(this);
  }
}
