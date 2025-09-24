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
package org.candlepin.subscriptions.liquibase.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import liquibase.integration.commandline.LiquibaseCommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationService {
  private static final Logger log = LoggerFactory.getLogger(MigrationService.class);
  public static final String HOST_VAR = "DATABASE_HOST";
  public static final String PORT_VAR = "DATABASE_PORT";
  public static final String DATABASE_VAR = "DATABASE_DATABASE";

  public static final String USERNAME_VAR = "DATABASE_USERNAME";
  public static final String PASSWORD_VAR = "DATABASE_PASSWORD";
  public static final String LIQUIBASE_COMMAND_USERNAME = "liquibase.command.username";
  public static final String LIQUIBASE_COMMAND_PASSWORD = "liquibase.command.password";
  public static final String LIQUIBASE_COMMAND_URL = "liquibase.command.url";

  private static String host = System.getenv(HOST_VAR);
  private static String port = System.getenv(PORT_VAR);
  private static String database = System.getenv(DATABASE_VAR);

  private static String username = System.getenv(USERNAME_VAR);
  private static String password = System.getenv(PASSWORD_VAR);

  // See https://docs.liquibase.com/parameters/home.html
  protected static final String[] DEFAULT_ARGS =
      new String[] {"--log-level=INFO", "--show-banner=false"};

  enum Context {
    CONTRACTS("contracts"),
    CORE("core"),
    ALL("_ALL");

    public final String value;
    public final String defaultsFile;

    String getValue() {
      return value;
    }

    private static final Map<String, Context> VALUE_ENUM_MAP = Context.initializeImmutableMap();

    Context(String value) {
      this.value = value;
      this.defaultsFile = value + ".properties";
    }

    /**
     * Maps lowercase string values of an enum's .getValue() to the enum itself
     *
     * @return Map where keys are lowercase string value of the given enumClass's value
     */
    static Map<String, Context> initializeImmutableMap() {
      return Arrays.stream(Context.class.getEnumConstants())
          .collect(Collectors.toMap(t -> t.getValue().toLowerCase(), value -> value));
    }

    /**
     * Parse the Context from its string representation
     *
     * @param value String representation of the Context
     * @return the Context enum
     */
    static Context fromString(String value) {
      String key = Objects.nonNull(value) ? value.toLowerCase() : null;

      return VALUE_ENUM_MAP.getOrDefault(key, ALL);
    }
  }

  /**
   * Liquibase uses its own environment variables, but we want to continue using our own. By pushing
   * the values from our custom environment variables into system properties, we can provide
   * developers with the flexibility to completely control the Liquibase invocation through command
   * line arguments since CLI arguments take priority over system properties. See the Configuration
   * hierarchy section of <a href="https://docs.liquibase.com/parameters/home.html">the
   * documentation</a>.
   */
  public static void populateSystemProperties() {
    if (Objects.nonNull(username)) {
      log.info(
          "Setting system property {} from environment variable {}",
          LIQUIBASE_COMMAND_USERNAME,
          USERNAME_VAR);
      System.setProperty(LIQUIBASE_COMMAND_USERNAME, username);
    }

    if (Objects.nonNull(password)) {
      log.info(
          "Setting system property {} from environment variable {}",
          LIQUIBASE_COMMAND_PASSWORD,
          PASSWORD_VAR);
      System.setProperty(LIQUIBASE_COMMAND_PASSWORD, password);
    }

    if (Stream.of(host, port, database).allMatch(Objects::nonNull)) {
      log.info(
          "Setting system property {} from environment variables {}, {}, {}",
          LIQUIBASE_COMMAND_URL,
          HOST_VAR,
          PORT_VAR,
          DATABASE_VAR);
      System.setProperty(
          LIQUIBASE_COMMAND_URL, "jdbc:postgresql://%s:%s/%s".formatted(host, port, database));
    } else if (Stream.of(host, port, database).allMatch(Objects::isNull)) {
      log.debug("No DATABASE_* environment variables detected");
    } else {
      throw new IllegalStateException(
          "DATABASE_HOST, DATABASE_PORT, and DATABASE_DATABASE must "
              + "all be defined or undefined.  A mixture of defined and undefined variables is not "
              + "allowed");
    }
  }

  public static void main(String[] invocationArgs) {
    // If nothing has been provided on the command line, assume we are running as a ClowdApp and
    // just perform a migration for all managed contexts
    if (invocationArgs.length == 0) {
      var contexts =
          Arrays.stream(Context.class.getEnumConstants())
              .map(Context::getValue)
              .filter(x -> x.startsWith("_"))
              .toList();
      log.info(
          "No invocation arguments provided.  Running update for the {} contexts based on "
              + "environment variables and properties files.",
          contexts);
      log.info(
          "If you wish to provide arguments via a `mvnw exec:java` use `-Dexec.args=` and specify "
              + "the context as the first argument followed by Liquibase commands and arguments");
      invocationArgs = new String[] {"update"};
    }

    populateSystemProperties();

    // If the zeroth argument doesn't match a context, then all contexts will be run.  E.g. if
    // the zeroth argument is "update", update will be run for both contexts.
    Context userContext = Context.fromString(invocationArgs[0]);

    Context[] migrationContexts;
    // Due to the history of the project, several tables were created in core that were later moved
    // to the control of contracts.  Liquibase has strong objections to modifying a changeset once
    // it has been applied, so the course of action we took was to create the table in contracts
    // only if it does not already exist.  (The table would be created in cases where only the
    // contract migrations were run during component testing).  A sideeffect of this solution is
    // that if the contracts migrations run first, the core migrations will fail since they
    // will also try to create the same tables as core only the legacy changesets won't have the
    // conditional to check if the table already exists.  Consequently, the core migrations must be
    // run first.
    if (userContext == Context.ALL) {
      migrationContexts = new Context[] {Context.CORE, Context.CONTRACTS};
    } else {
      migrationContexts = new Context[] {userContext};
      // Strip the context off.  We don't want to send that to the Liquibase CLI
      invocationArgs = Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length);
    }

    log.info("Migration scheduled for {}", List.of(migrationContexts));

    TimeZone currentZone = TimeZone.getDefault();
    boolean success = true;

    try {
      // The databasechangelog table stores the execution date in a timestamp without time zone
      // column and Liquibase uses whatever the default system zone is.  We try to use UTC
      // everywhere so set the zone to UTC temporarily.
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      for (Context c : migrationContexts) {
        log.info("Migrating {}", c.getValue());
        String[] fileArgs = {"--defaults-file=" + c.defaultsFile};
        String[] passedArgs = joinArgs(DEFAULT_ARGS, fileArgs, invocationArgs);

        // Cast to Object to prevent interpretation as varargs
        log.info("Calling Liquibase with arguments {}", (Object) passedArgs);
        int returnVal = new LiquibaseCommandLine().execute(passedArgs);
        if (returnVal != 0) {
          success = false;
        }
        log.info("Migrating {} complete with status {}", c.getValue(), returnVal);
      }
    } finally {
      TimeZone.setDefault(currentZone);
    }

    log.info("Migration complete");
    System.exit((success) ? 0 : 1);
  }

  public static String[] joinArgs(String[]... args) {
    return Stream.of(args).flatMap(Stream::of).toArray(String[]::new);
  }
}
