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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Context for executing statements within a transaction. */
public class TransactionContext {
  private final Connection connection;

  public TransactionContext(Connection connection) {
    this.connection = connection;
  }

  /**
   * Execute a SQL update statement with parameters.
   *
   * @param sql the SQL statement
   * @param params parameters to bind
   * @return number of rows affected
   */
  public int executeUpdate(String sql, Object... params) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      this.bindParameters(ps, params);
      return ps.executeUpdate();
    }
  }

  /**
   * Execute a SQL update statement with a custom parameter binder.
   *
   * @param sql the SQL statement
   * @param binder function to bind parameters to the prepared statement
   * @return number of rows affected
   */
  public int executeUpdate(String sql, ParameterBinder binder) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      binder.bind(ps);
      return ps.executeUpdate();
    }
  }

  /**
   * Get the underlying connection for custom operations.
   *
   * @return the connection
   */
  public Connection getConnection() {
    return connection;
  }

  private void bindParameters(PreparedStatement ps, Object... params) throws SQLException {
    for (int i = 0; i < params.length; i++) {
      ps.setObject(i + 1, params[i]);
    }
  }
}
