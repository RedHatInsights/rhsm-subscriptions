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

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

// @Disabled("This placeholder test shows how to setup an integration test w/ DB & Kafka")
@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(KafkaResource.class)
class TestContainerTest {

  @Inject DataSource dataSource;

  @Test
  void testContainersStarting() {
    Assert.assertTrue(true);
  }

  @Test
  void testPgVersion() {
    try(Statement statement = dataSource.getConnection().createStatement()) {
      statement.execute("select version()");
      statement.getResultSet().next();
      assertTrue(statement.getResultSet().getString(1).contains("PostgreSQL"));
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
