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
package com.redhat.swatch.component.tests.api.db;

import com.redhat.swatch.component.tests.core.BaseService;
import com.redhat.swatch.component.tests.exceptions.ServiceException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * PostgreSQL database service providing connection management, transaction, and query execution
 * logic.
 *
 * <p>Properties (host, port, database, username, password, sslmode) are set by the managed resource
 * before the service starts.
 */
public class DatabaseService extends BaseService<DatabaseService> {

  public DatabaseService() {}

  /**
   * Get a JDBC connection to the database.
   *
   * @return a new database connection
   * @throws SQLException if connection fails
   */
  public Connection getConnection() throws SQLException {
    String username = getRequiredProperty("username");
    String password = getRequiredProperty("password");
    return DriverManager.getConnection(getJdbcUrl(), username, password);
  }

  /**
   * Execute multiple SQL statements in a single transaction.
   *
   * @param operation the transaction operation
   */
  public void executeInTransaction(TransactionOperation operation) {
    try (Connection conn = getConnection()) {
      conn.setAutoCommit(false);
      try {
        operation.execute(new TransactionContext(conn));
        conn.commit();
      } catch (Exception e) {
        conn.rollback();
        throw new ServiceException("Transaction failed: " + e.getMessage(), e);
      }
    } catch (SQLException e) {
      throw new ServiceException("Failed to execute transaction: " + e.getMessage(), e);
    }
  }

  /** Build the JDBC URL for this PostgreSQL database. */
  private String getJdbcUrl() {
    String host = getRequiredProperty("host");
    int port = Integer.parseInt(getRequiredProperty("port"));
    String database = getRequiredProperty("database");

    String baseUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);

    // Add SSL mode if configured
    String sslMode = getProperty("sslmode", null);
    if (sslMode != null && !sslMode.isBlank()) {
      baseUrl += "?sslmode=" + sslMode;
    }

    return baseUrl;
  }

  private String getRequiredProperty(String key) {
    String value = getProperty(key, null);
    if (value == null) {
      throw new ServiceException(
          String.format(
              "Required property '%s' not set. Ensure the database managed resource has started.",
              key));
    }
    return value;
  }
}
