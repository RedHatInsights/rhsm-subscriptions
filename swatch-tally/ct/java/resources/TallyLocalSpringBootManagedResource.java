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
package resources;

import com.redhat.swatch.component.tests.resources.springboot.LocalSpringBootManagedResource;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Custom Spring Boot managed resource for tally component tests that enables OpenTelemetry support
 * via the Splunk OTEL Java agent.
 */
public class TallyLocalSpringBootManagedResource extends LocalSpringBootManagedResource {

  private static final String OTEL_JAVAAGENT_PROPERTY = "OTEL_JAVAAGENT_ENABLED";
  private static final String OTEL_EXPORTER_OTLP_ENDPOINT_PROPERTY = "OTEL_EXPORTER_OTLP_ENDPOINT";
  private static final String OTEL_EXPORTER_OTLP_PROTOCOL_PROPERTY = "OTEL_EXPORTER_OTLP_PROTOCOL";
  private static final String OTEL_SERVICE_NAME_PROPERTY = "OTEL_SERVICE_NAME";

  private static final String DEFAULT_OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317";
  private static final String DEFAULT_OTEL_EXPORTER_OTLP_PROTOCOL = "grpc";
  private static final String DEFAULT_OTEL_SERVICE_NAME = "swatch-tally";

  public TallyLocalSpringBootManagedResource(String service) {
    super(service);
    // Log that our custom resource is being used (for debugging)
    System.out.println("TallyLocalSpringBootManagedResource initialized for service: " + service);
  }

  @Override
  protected void init(com.redhat.swatch.component.tests.core.ServiceContext context) {
    super.init(context);

    // Check if OTEL is enabled via property (default: true for component tests)
    String otelEnabled = context.getOwner().getProperty(OTEL_JAVAAGENT_PROPERTY).orElse("true");
    if (!Boolean.parseBoolean(otelEnabled)) {
      return; // OTEL disabled, skip configuration
    }

    try {
      configureOtelJavaAgent();
      configureOtelProperties(context);
    } catch (Exception e) {
      // Log error but don't fail the test - OTEL is optional
      System.err.println("WARNING: Failed to configure OTEL: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void configureOtelJavaAgent() {
    try {
      // Path to the Java agent from project root
      // DevProcessManagedResource sets location to project root (Paths.get("").resolve("../.."))
      // The service runs from that location, so we resolve the agent path relative to project root
      Path projectRoot = Paths.get("").resolve("../..").toAbsolutePath().normalize();
      Path agentPath =
          projectRoot
              .resolve("swatch-spring-parent")
              .resolve("target")
              .resolve("javaagent")
              .resolve("splunk-otel-javaagent.jar");

      File agentFile = agentPath.toFile();
      if (!agentFile.exists()) {
        // If agent doesn't exist, log a warning but don't fail
        // The agent will be built during the Maven build process
        System.err.println(
            "WARNING: OTEL Java agent not found at: "
                + agentPath
                + ". Make sure swatch-spring-parent has been built.");
        return;
      }

      // Add Java agent JVM argument
      // Use absolute path to ensure it works regardless of working directory
      String javaAgentArg = "-javaagent:" + agentFile.getAbsolutePath();
      addJvmArgument(javaAgentArg);
    } catch (Exception e) {
      // Log error but don't fail the test - OTEL is optional
      System.err.println("WARNING: Failed to configure OTEL Java agent: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void configureOtelProperties(
      com.redhat.swatch.component.tests.core.ServiceContext context) {
    // Configure OTEL environment variables as system properties
    String otelEndpoint =
        context
            .getOwner()
            .getProperty(OTEL_EXPORTER_OTLP_ENDPOINT_PROPERTY)
            .orElse(DEFAULT_OTEL_EXPORTER_OTLP_ENDPOINT);
    String otelProtocol =
        context
            .getOwner()
            .getProperty(OTEL_EXPORTER_OTLP_PROTOCOL_PROPERTY)
            .orElse(DEFAULT_OTEL_EXPORTER_OTLP_PROTOCOL);
    String otelServiceName =
        context
            .getOwner()
            .getProperty(OTEL_SERVICE_NAME_PROPERTY)
            .orElse(DEFAULT_OTEL_SERVICE_NAME);

    // Set as system properties that will be passed to the Spring Boot process
    addJvmArgument("-D" + OTEL_EXPORTER_OTLP_ENDPOINT_PROPERTY + "=" + otelEndpoint);
    addJvmArgument("-D" + OTEL_EXPORTER_OTLP_PROTOCOL_PROPERTY + "=" + otelProtocol);
    addJvmArgument("-D" + OTEL_SERVICE_NAME_PROPERTY + "=" + otelServiceName);
    addJvmArgument("-D" + OTEL_JAVAAGENT_PROPERTY + "=true");
  }

  /**
   * Add a JVM argument to the Spring Boot run configuration. This method accesses the protected
   * propertiesToOverwrite map to add arguments, similar to how the parent class's private
   * addArguments method works.
   */
  private void addJvmArgument(String argument) {
    String property = "spring-boot.run.jvmArguments";
    String previous = propertiesToOverwrite.get(property);
    if (previous != null) {
      argument = previous + " " + argument;
    }
    propertiesToOverwrite.put(property, argument);
  }
}
