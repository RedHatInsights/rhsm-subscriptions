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
package com.redhat.swatch.component.tests.doctor.tools;

import dev.langchain4j.agent.tool.Tool;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * Tools that allow the AI agent to query the rhsm-subscriptions test database. Useful for checking
 * test data, verifying state, understanding schema, etc.
 */
@ApplicationScoped
public class DatabaseTools {

  @Inject
  @DataSource("rhsm-subscriptions")
  AgroalDataSource dataSource;

  /**
   * Execute a SQL query on the database and return results as formatted text. Only SELECT queries
   * are allowed for safety.
   *
   * @param sql the SQL query to execute (must be SELECT)
   * @return query results formatted as text, or error message
   */
  @Tool(
      "Execute a SQL SELECT query on the rhsm-subscriptions test database. "
          + "Useful for checking test data, verifying database state, or understanding schema. "
          + "Only SELECT queries allowed. Max 50 rows returned. "
          + "Example: executeQuery(\"SELECT * FROM hosts LIMIT 5\")")
  public String executeQuery(String sql) {
    Log.debugf("Executing SQL query: %s", sql);

    // Safety check - only allow SELECT queries
    String trimmedSql = sql.trim().toLowerCase();
    if (!trimmedSql.startsWith("select")) {
      return "Error: Only SELECT queries are allowed for safety. Your query: " + sql;
    }

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      // Execute query with timeout
      stmt.setQueryTimeout(10); // 10 seconds max

      try (ResultSet rs = stmt.executeQuery(sql)) {
        return formatResultSet(rs);
      }

    } catch (Exception e) {
      Log.error("Error executing query", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Format a ResultSet as a readable text table.
   *
   * @param rs the result set to format
   * @return formatted table as string
   */
  private String formatResultSet(ResultSet rs) throws Exception {
    ResultSetMetaData metaData = rs.getMetaData();
    int columnCount = metaData.getColumnCount();

    StringBuilder result = new StringBuilder();

    // Header
    result.append("Query Results:\n\n");
    for (int i = 1; i <= columnCount; i++) {
      result.append(metaData.getColumnName(i));
      if (i < columnCount) {
        result.append(" | ");
      }
    }
    result.append("\n");
    result.append("-".repeat(80)).append("\n");

    // Rows (limit to 50 rows to avoid overwhelming the LLM)
    int rowCount = 0;
    int maxRows = 50;

    while (rs.next() && rowCount < maxRows) {
      for (int i = 1; i <= columnCount; i++) {
        Object value = rs.getObject(i);
        result.append(value != null ? value.toString() : "NULL");
        if (i < columnCount) {
          result.append(" | ");
        }
      }
      result.append("\n");
      rowCount++;
    }

    if (rs.next()) {
      result.append("... (more rows, showing first ").append(maxRows).append(")\n");
    }

    result.append("\nTotal rows shown: ").append(rowCount).append("\n");

    return result.toString();
  }
}
