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

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import liquibase.Scope;
import liquibase.change.custom.CustomTaskChange;
import liquibase.change.custom.CustomTaskRollback;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.logging.Logger;
import liquibase.resource.ResourceAccessor;

/**
 * The LiquibaseCustomTask class provides some common utility functions for performing queries or
 * generating UUIDs for new objects.
 */
public abstract class LiquibaseCustomTask
    implements CustomTaskChange, CustomTaskRollback, Closeable {
  protected JdbcConnection connection;
  protected Logger logger;

  private Map<String, PreparedStatement> preparedStatements;

  protected LiquibaseCustomTask() {
    this.logger = getLogger();
    this.preparedStatements = new HashMap<>();
  }

  @SuppressWarnings("java:S112")
  @Override
  public void close() {
    try {
      for (PreparedStatement statement : this.preparedStatements.values()) {
        statement.close();
      }

      this.preparedStatements.clear();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Sets the parameter at the specified index to the given value. This method attempts to perform
   * safe assignment of parameters across all supported platforms.
   *
   * @param statement the statement on which to set a parameter
   * @param index the index of the parameter to set
   * @param value the value to set
   * @throws NullPointerException if statement is null
   * @return the PreparedStatement being updated
   */
  protected PreparedStatement setParameter(PreparedStatement statement, int index, Object value)
      throws SQLException {

    if (value != null) {
      statement.setObject(index, value);
    } else {
      // NB: If Oracle support is ever required, Oracle will want Types.VARCHAR here.  See
      // http://stackoverflow.com/questions/11793483/setobject-method-of-preparedstatement
      statement.setNull(index, Types.NULL);
    }

    return statement;
  }

  /**
   * Fills the parameters of a prepared statement with the given arguments
   *
   * @param statement the statement to fill
   * @param argv the collection of arguments with which to fill the statement's parameters
   * @throws NullPointerException if statement is null
   * @return the provided PreparedStatement
   */
  protected PreparedStatement fillStatementParameters(PreparedStatement statement, Object... argv)
      throws SQLException {

    statement.clearParameters();

    if (argv != null) {
      for (int i = 0; i < argv.length; ++i) {
        this.setParameter(statement, i + 1, argv[i]);
      }
    }

    return statement;
  }

  /**
   * Prepares a statement and populates it with the specified arguments, pulling from cache when
   * possible.
   *
   * @param sql The SQL to execute. The given SQL may be parameterized.
   * @param argv The arguments to use when executing the given query.
   * @return a PreparedStatement instance representing the specified SQL statement
   */
  @SuppressWarnings("squid:S3824")
  protected PreparedStatement prepareStatement(String sql, Object... argv)
      throws DatabaseException, SQLException {

    PreparedStatement statement = this.preparedStatements.get(sql);
    if (statement == null) {
      statement = this.connection.prepareStatement(sql);
      this.preparedStatements.put(sql, statement);
    }

    return this.fillStatementParameters(statement, argv);
  }

  /**
   * Executes the given SQL query.
   *
   * @param sql The SQL to execute. The given SQL may be parameterized.
   * @param argv The arguments to use when executing the given query.
   * @return A ResultSet instance representing the result of the query.
   */
  protected ResultSet executeQuery(String sql, Object... argv)
      throws DatabaseException, SQLException {
    PreparedStatement statement = this.prepareStatement(sql, argv);
    return statement.executeQuery();
  }

  protected ResultSet executeQuery(String sql) throws DatabaseException, SQLException {
    PreparedStatement statement = this.prepareStatement(sql);
    return statement.executeQuery();
  }

  /**
   * Executes the given SQL update/insert/delete
   *
   * @param sql The SQL to execute. The given SQL may be parameterized.
   * @param argv The arguments to use when executing the given update.
   * @return The number of rows affected by the update.
   */
  protected int executeUpdate(String sql, Object... argv) throws DatabaseException, SQLException {
    PreparedStatement statement = this.prepareStatement(sql, argv);
    return statement.executeUpdate();
  }

  protected int executeUpdate(String sql) throws DatabaseException, SQLException {
    PreparedStatement statement = this.prepareStatement(sql);
    return statement.executeUpdate();
  }

  /**
   * Generates a 32-character UUID to use with object creation/migration.
   *
   * <p>The UUID is generated by creating a "standard" UUID and removing the hyphens. The UUID may
   * be standardized by reinserting the hyphens later, if necessary.
   *
   * @return a 32-character UUID
   */
  protected String generateUUID() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * Executes this liquibase upgrade task.
   *
   * @throws DatabaseException if an error occurs while performing a database operation
   * @throws CustomChangeException if an error occurs with changeset expectations
   * @throws SQLException if an error occurs while executing an SQL statement
   */
  public abstract void executeTask(Database database)
      throws DatabaseException, SQLException, CustomChangeException;

  @Override
  public void execute(Database database) throws CustomChangeException {
    if (database == null) {
      throw new IllegalArgumentException("database is null");
    }

    if (!JdbcConnection.class.isAssignableFrom(database.getConnection().getClass())) {
      throw new CustomChangeException("database connection is not a JDBC connection");
    }

    this.connection = (JdbcConnection) database.getConnection();

    Boolean autocommit = null;
    try {
      // Store the connection's auto commit setting, so we may temporarily clobber it.
      autocommit = this.connection.getAutoCommit();
      if (disableAutoCommit()) {
        this.connection.setAutoCommit(false);
      }
      this.executeTask(database);
    } catch (Exception e) {
      throw new CustomChangeException(e);
    } finally {
      try {
        if (autocommit != null) {
          this.connection.setAutoCommit(autocommit);
        }
      } catch (DatabaseException e) {
        logger.severe("Could not reset autocommit");
      }
      close();
    }
  }

  // Courtesy https://stackoverflow.com/a/2861510
  public String preparePlaceHolders(int length) {
    return String.join(",", Collections.nCopies(length, "?"));
  }

  @Override
  public void setUp() throws SetupException {}

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {}

  @Override
  public ValidationErrors validate(Database database) {
    return null;
  }

  public abstract boolean disableAutoCommit();

  public Logger getLogger() {
    return Scope.getCurrentScope().getLog(this.getClass());
  }
}
