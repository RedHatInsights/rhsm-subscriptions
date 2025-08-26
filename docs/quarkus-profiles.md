# Quarkus Profiles Reference

This document describes the various Quarkus profiles used across the RHSM Subscriptions project and their purposes.

## Overview

Quarkus profiles allow different configurations to be applied based on the runtime environment. These profiles are activated using the `QUARKUS_PROFILE` environment variable or through the Makefile which sets profiles automatically for local development.

## Available Profiles

### `dev` - Development Profile

**Purpose**: Local development environment configuration

**Key Features**:
- **Debug Logging**: Sets `LOGGING_LEVEL_COM_REDHAT_SWATCH=DEBUG` for detailed logging
- **Local Ports**: Each service runs on a specific development port (e.g., swatch-contracts on 8011, swatch-billable-usage on 8012)
- **Management Ports**: Management/metrics endpoints run on port + 1000 (e.g., 9011, 9012)
- **Mock Services**: Points to local WireMock services for external dependencies
- **Splunk Disabled**: `ENABLE_SPLUNK_HEC=false` - logging goes to console instead
- **CORS Relaxed**: `CORS_ORIGINS=/.*/` - allows any origin for local testing
- **Security Disabled**: Authentication disabled in dev mode for easier testing
- **Live Reload**: Quarkus live reload enabled with configurable password

**Example Configuration**:
```properties
%dev.SERVER_PORT=8011
%dev.QUARKUS_MANAGEMENT_PORT=9011
%dev.LOGGING_LEVEL_COM_REDHAT_SWATCH=DEBUG
%dev.ENABLE_SPLUNK_HEC=false
%dev.CORS_ORIGINS=/.*/
%dev.SWATCH_SELF_PSK=placeholder
```

**Usage**: Automatically applied when using the Makefile (e.g., `make swatch-contracts`)

### `test` - Test Profile

**Purpose**: Unit and integration test execution

**Key Features**:
- **In-Memory Database**: Uses H2 database instead of PostgreSQL for faster tests
- **Disabled Features**: Management endpoints and external integrations disabled
- **Inherits Dev Config**: Many properties inherit from the dev profile for consistency
- **Test-Specific Overrides**: Host name set to "unit_tests" for identification
- **Dynamic Ports**: Uses dynamic port allocation to avoid conflicts during parallel test execution

**Example Configuration**:
```properties
%test.quarkus.datasource.db-kind=h2
%test.quarkus.management.enabled=false
%test.HOST_NAME=unit_tests
%test.quarkus.http.test-port=0
```

**Usage**: Automatically activated by `@QuarkusTest` annotations in test classes

### `ephemeral` - Ephemeral Environment Profile

**Purpose**: Temporary testing environments (CI/CD, PR previews)

**Key Features**:
- **Fast Aggregation**: Reduced time windows for billable usage aggregation (3s instead of 3600s)
- **Mock Endpoints**: Uses WireMock endpoints for external services
- **OpenTelemetry Disabled**: `DISABLE_OTEL=true` for simpler ephemeral deployments
- **Test APIs Enabled**: Allows access to test-only API endpoints
- **Reduced Grace Periods**: Faster processing for quick validation

**Example Configuration**:
```properties
%ephemeral.DISABLE_OTEL=true
%ephemeral.KSTREAM_BILLABLE_USAGE_AGGREGATION_WINDOW_DURATION=3s
%ephemeral.KSTREAM_BILLABLE_USAGE_AGGREGATION_GRACE_DURATION=0s
%ephemeral.SWATCH_TEST_APIS_ENABLED=true
```

**Usage**: Set via deployment configurations in CI/CD pipelines

### `stage` - Staging Environment Profile

**Purpose**: Pre-production staging environment

**Key Features**:
- **Staging APIs**: Points to staging versions of external Red Hat services
- **Test APIs Enabled**: `SWATCH_TEST_APIS_ENABLED=true` for validation
- **Production-Like Config**: Similar to prod but with test data and relaxed security
- **Azure Features Enabled**: `AZURE_MANUAL_SUBMISSION_ENABLED=true` for testing
- **OpenTelemetry Enabled**: Full observability stack enabled

**Example Configuration**:
```properties
%stage.ENTITLEMENT_GATEWAY_URL=https://ibm-entitlement-gateway.stage.api.redhat.com
%stage.SUBSCRIPTION_URL=https://subscription.stage.api.redhat.com/svcrest/subscription/v5
%stage.SWATCH_TEST_APIS_ENABLED=true
%stage.AZURE_MANUAL_SUBMISSION_ENABLED=true
```

**Usage**: Automatically applied in staging OpenShift environments

### `prod` - Production Profile

**Purpose**: Production environment

**Key Features**:
- **Production APIs**: Points to production Red Hat services
- **Test APIs Disabled**: `SWATCH_TEST_APIS_ENABLED=false` for security
- **Full Observability**: OpenTelemetry and Splunk logging enabled
- **Secure Configuration**: Strict CORS, authentication required, secure defaults
- **Subscription Editing Disabled**: `DEVTEST_SUBSCRIPTION_EDITING_ENABLED=false`

**Example Configuration**:
```properties
%prod.ENTITLEMENT_GATEWAY_URL=https://ibm-entitlement-gateway.api.redhat.com
%prod.SUBSCRIPTION_URL=https://subscription.api.redhat.com/svcrest/subscription/v5
%prod.SWATCH_TEST_APIS_ENABLED=false
%prod.DEVTEST_SUBSCRIPTION_EDITING_ENABLED=false
```

**Usage**: Applied in production OpenShift environments

### `qa` - Quality Assurance Profile

**Purpose**: QA testing environment

**Key Features**:
- **QA APIs**: Points to QA versions of external services
- **Similar to Stage**: Shares many characteristics with staging environment
- **Testing Focus**: Optimized for QA validation workflows

**Example Configuration**:
```properties
%qa.ENTITLEMENT_GATEWAY_URL=https://ibm-entitlement-gateway.qa.api.redhat.com
```

**Usage**: Applied in QA OpenShift environments

## Profile Activation

### Local Development
Profiles are automatically managed by the Makefile:
```bash
# Automatically includes 'dev' profile
make swatch-contracts PROFILES=dev,other_profile
```

### Environment Variable
```bash
export QUARKUS_PROFILE=dev,ephemeral
```

### Multiple Profiles
Profiles can be combined using comma separation:
```bash
QUARKUS_PROFILE=dev,kafka-queue
```

## Common Configuration Patterns

### Database Configuration
- **Production/Stage**: PostgreSQL with connection pooling
- **Dev**: PostgreSQL with debug logging
- **Test**: H2 in-memory database

### External Service Integration
- **Production/Stage/QA**: Real Red Hat service endpoints
- **Dev/Ephemeral**: WireMock mock services
- **Test**: Disabled or mocked

### Observability
- **Production/Stage**: Full OpenTelemetry and Splunk integration
- **Dev**: Console logging only
- **Test**: Minimal logging
- **Ephemeral**: OpenTelemetry disabled for simplicity

### Security
- **Production**: Full authentication and authorization
- **Stage/QA**: Production-like security with test accounts
- **Dev**: Security disabled for ease of development
- **Test**: Security disabled

## Service-Specific Profiles

Different services may have additional profile-specific configurations:

- **swatch-billable-usage**: Kafka Streams configurations with different window durations
- **swatch-producer-aws**: AWS-specific endpoint configurations
- **swatch-producer-azure**: Azure-specific authentication settings
- **swatch-contracts**: Subscription service integration settings

## Best Practices

1. **Profile Inheritance**: Test profiles often inherit from dev profiles for consistency
2. **Environment Isolation**: Each environment has its own set of external service endpoints
3. **Resource Optimization**: Ephemeral environments use reduced resource configurations
4. **Security Layering**: Security settings become progressively stricter from dev to prod
5. **Observability Gradation**: Logging and monitoring become more comprehensive in higher environments

## Related Documentation

- [Application Configuration](../README.md#environment-variables)
- [Local Development Setup](../README.md#local-development)
- [Deployment Guide](../README.md#deployment-notes)