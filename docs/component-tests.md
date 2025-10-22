# Component Testing Framework

## Overview

The Component Testing Framework is a comprehensive testing solution designed for SWATCH services that enables developers to write and execute integration tests in two distinct environments:

1. **Local Environment**: Tests run locally using containers managed by Testcontainers and Docker Compose
2. **Ephemeral OpenShift Environment**: Tests run against a deployed Bonfire environment in OpenShift

This framework provides a unified testing approach that allows the same test code to execute seamlessly in both environments, enabling developers to validate their services' behavior in isolation (locally) and in a more production-like environment (OpenShift).

### Scope

| Feature | Link | Team | Status |
|------------|------|------|--------|
| Ability to read cdappconfig.json in each SWATCH service for service discovery | [SWATCH-4107](https://issues.redhat.com/browse/SWATCH-4107) | TBD | Needs refinement |

### Key Features

- **Dual Environment Support**: Write once, run anywhere - tests can execute both locally and against OpenShift deployments
- **Container Management (Partially)**: Automatic lifecycle management of dependencies using Testcontainers for local execution
- **Annotation-Driven Configuration**: Simple, declarative test setup using Java annotations
- **Logging and Debugging**: Comprehensive logging capabilities for troubleshooting test failures
- **Maven Integration**: Seamless integration with Maven build profiles for different execution modes
- **Testing Best Practices**: Built-in support for testing good practices including:
    - **Random Port Assignment**: Services automatically use random available ports when running locally, preventing port conflicts between parallel test executions
    - **Test Isolation**: Each test gets its own isolated environment with separate service instances
    - **Resource Cleanup**: Automatic cleanup of containers, ports, and temporary resources after test completion
    - **Deterministic Testing**: Framework ensures consistent test behavior across different environments and runs
    - **Parallel Execution Safety**: Tests can run in parallel without interfering with each other through proper resource isolation
    - **Fail-Fast Mechanisms**: Quick detection and reporting of service startup failures to reduce debugging time
    - **Health Checks**: Automatic health verification before tests begin execution to ensure services are ready

### Supported Services

- **Kafka**: Built-in support for Kafka messaging patterns using Kafka Bridge
- **Wiremock**: Easy mocking of external services and APIs

## Getting Started

The `@ComponentTest` annotation is the entry point for using the framework. It extends JUnit 5 tests with the ComponentTestExtension, which orchestrates the entire test lifecycle.

```java
@ComponentTest
@Tag("component")
public class MyServiceTest {
    // Test implementation
}
```

**Running Tests Locally (Default)**
By default, tests run locally using containers. No additional configuration is needed:

```java
@ComponentTest
public class MyLocalTest {
    // Runs locally with containers
}
```

**Running Tests in OpenShift**
To execute tests against an OpenShift environment, you have two options:

1. **Using the @RunOnOpenShift annotation:**
```java
@ComponentTest
@RunOnOpenShift
public class MyOpenShiftTest {
    // Runs against OpenShift deployment
}
```

2. **Using the system property:**
```bash
./mvnw clean install -Pcomponent-tests -Dservice=my-service -Dswatch.component-tests.global.target=openshift
```

### Adding a New Component Test Project

To add component tests for a new service, follow these steps:

1. **Create the component test directory structure:**
```
your-service/
├── ct/
│   ├── java/
│   │   └── tests/
│   │       ├── BaseYourServiceComponentTest.java
│   │       └── SimpleYourServiceComponentTest.java
│   └── README.md
└── pom.xml
```

2. **Add your service to the Maven profile:**
   Edit the root `pom.xml` and add your service to the `component-tests` profile:

```xml
<profile>
  <id>component-tests</id>
  <modules>
    <!-- existing services -->
    <module>swatch-producer-azure/ct</module>
    <module>swatch-contracts/ct</module>
    <!-- add your new service -->
    <module>your-service/ct</module>
  </modules>
</profile>
```

3. **Define the service property:**
   When running tests, specify your service name using the `service` property:

```bash
./mvnw clean install -Pcomponent-tests -Dservice=your-service
```

4. **Use minimal dependencies:**
   Keep your component test dependencies minimal. Only include what you actually need and do not include the service dependencies because this would require a full build when running the Konflux pipelines.

5. **Create the component test pom.xml:**
   Create a `pom.xml` file in your `your-service/ct/` directory:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.redhat.swatch</groupId>
    <artifactId>swatch-quarkus-parent</artifactId>
    <version>1.1.0-SNAPSHOT</version>
    <relativePath>../../swatch-quarkus-parent/pom.xml</relativePath>
  </parent>

  <artifactId>your-service-component-tests</artifactId>
  <name>SWATCH - Services - Your Service - Component Tests</name>

  <dependencies>
    <dependency>
      <groupId>com.redhat.swatch</groupId>
      <artifactId>swatch-test-framework</artifactId>
    </dependency>
  </dependencies>

  <profiles>
    <profile>
      <id>component-tests-by-service</id>
      <activation>
        <activeByDefault>false</activeByDefault>
        <property>
          <name>service</name>
          <value>your-service</value>
        </property>
      </activation>

      <build>
        <plugins>
          <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <executions>
              <execution>
                <id>add-ct-sources</id>
                <phase>generate-test-sources</phase>
                <goals>
                  <goal>add-test-source</goal>
                </goals>
                <configuration>
                  <sources>
                    <source>${project.basedir}/java</source>
                  </sources>
                </configuration>
              </execution>
              <execution>
                <id>add-ct-resources</id>
                <phase>generate-test-resources</phase>
                <goals>
                  <goal>add-test-resource</goal>
                </goals>
                <configuration>
                  <resources>
                    <resource>
                      <directory>${project.basedir}/resources</directory>
                    </resource>
                  </resources>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
</project>
```

6. **Create the README.md file:**
   Create a `README.md` file in your `your-service/ct/` directory with instructions for running tests locally and against OpenShift. Check out [the swatch-utilization README.md file](https://github.com/RedHatInsights/rhsm-subscriptions/blob/main/swatch-utilization/ct/README.md) as a reference.

**Important Notes:**
- Adjust the `podman compose up` command to include only the services your component actually needs
- Modify the Bonfire deployment command to include only required dependencies
- Update the service name throughout the README to match your actual service name

## Architecture

The Component Testing Framework is built around several core architectural components that work together to provide a flexible and extensible testing platform.

### Core Components

#### Services
Services represent the core business logic components being tested. The framework provides several service types:

**SwatchService**: Represents SWATCH application services
- Manages Quarkus and Spring Boot applications
- Handles service lifecycle (start, stop, configuration)
- Provides access to service logs and properties

**KafkaBridgeService**: Manages Kafka messaging
- Produces and consumes Kafka messages
- Provides topic subscription capabilities
- Includes message validation utilities

**WiremockService**: Manages mock HTTP services
- Configures HTTP mocks for external dependencies
- Supports request/response verification
- Enables simulation of various scenarios (success, failure, timeouts)

#### ManagedResource
ManagedResource is an abstract base class that handles the actual runtime management of services. Different implementations exist for different environments:

**LocalContainerManagedResource**:
- Uses Testcontainers for local execution
- Manages Docker containers lifecycle
- Handles port mapping and networking

**OpenShiftContainerManagedResource**:
- Integrates with Kubernetes/OpenShift APIs
- Manages pod lifecycle and service discovery
- Handles OpenShift-specific networking and configuration

#### Annotation Bindings
The framework uses a plugin-based system for handling different service types through annotation bindings:

**@Quarkus**: Configures Quarkus applications
- Specifies service name and configuration
- Handles Quarkus-specific startup and health checks

**@SpringBoot**: Configures Spring Boot applications
- Manages Spring Boot service lifecycle
- Handles Spring-specific configuration patterns

**@Wiremock**: Configures Wiremock instances
- Sets up HTTP mocking capabilities
- Manages mock server lifecycle

**@KafkaBridge**: Configures Kafka bridge services
- Enables Kafka message production/consumption
- Manages topic subscriptions and message routing

**@RunOnOpenShift**: Configures OpenShift-specific behavior
- Controls OpenShift deployment options
- Manages additional resources and debugging options

### Extension System

The framework includes an extensible architecture through:

**ExtensionBootstrap**: Allows custom extensions to hook into the test lifecycle
**AnnotationBinding**: Enables custom service type support
**ServiceListener**: Provides callbacks for service lifecycle events

### Configuration Management

The framework supports flexible configuration through:

**ComponentTestConfiguration**: Global framework configuration
**ServiceConfiguration**: Per-service configuration options
**Property Injection**: Dynamic property resolution and injection

### Dependency Injection

The framework provides dependency injection capabilities:
- Automatic service discovery and injection
- Parameter resolution for test methods
- Context-aware resource management
