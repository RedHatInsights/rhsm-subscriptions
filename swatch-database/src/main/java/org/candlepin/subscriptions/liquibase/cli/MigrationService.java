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

  // See https://docs.liquibase.com/parameters/home.html
  public static final String[] DEFAULT_ARGS =
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
          "If you wish to provide arguments via a `gradlew run` use `--args=` and specify "
              + "the context as the first argument followed by Liquibase commands and arguments");
      invocationArgs = new String[] {"update"};
    }

    // If the zeroth argument doesn't match a context, then all contexts will be run.  E.g. if
    // the zeroth argument is "update", update will be run for both contexts.
    Context userContext = Context.fromString(invocationArgs[0]);

    Context[] migrationContexts;
    if (userContext == Context.ALL) {
      migrationContexts = new Context[] {Context.CONTRACTS, Context.CORE};
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
