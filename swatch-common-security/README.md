# SWATCH Common Security

A shared security library for SWATCH Quarkus applications providing authentication and authorization capabilities.

## Features

- **Multi-Authentication Support**: PSK (Pre-Shared Key) and Red Hat Identity header authentication
- **RBAC Integration**: Role-based access control via Red Hat RBAC service
- **Security Identity Augmentation**: Automatic role assignment based on user permissions
- **MDC Logging**: Automatic org_id injection into logging context
- **Configurable Security**: Enable/disable security features per environment

## Supported Authentication Methods

### 1. PSK Authentication
- Internal service-to-service authentication
- Uses `x-rh-swatch-psk` header
- Configurable PSK value per environment

### 2. Red Hat Identity Authentication  
- Customer and service account authentication
- Uses `x-rh-identity` header (base64 encoded JSON)
- Integrates with Red Hat RBAC service for role assignment

## Quick Start to integrate swatch-common-security library and enable security in the quarkus app

### 1. Add Dependency

Add to your Quarkus module's `pom.xml`:

```xml
<dependency>
    <groupId>com.redhat.swatch</groupId>
    <artifactId>swatch-common-security</artifactId>
</dependency>

<dependency>
    <groupId>com.redhat.swatch.clients.quarkus</groupId>    
    <artifactId>rbac-client</artifactId>
</dependency>
```

### 2. Configuration

Add to your `application.properties`:

```properties
# PSK Configuration (dev/test only - production should use clowdapp.yaml)
%dev.SWATCH_SELF_PSK=dev-placeholder
%test.SWATCH_SELF_PSK=test-placeholder

# RBAC Configuration
RBAC_ENABLED=true
%dev.RBAC_ENABLED=false
%test.RBAC_ENABLED=false

# Security Configuration
quarkus.security.deny-unannotated-members=true
quarkus.security.auth.enabled-in-dev-mode=false

# RBAC Service URL (provided by rbac-client dependency)
RBAC_ENDPOINT=${clowder.endpoints.rbac-service.url}
%dev.RBAC_ENDPOINT=http://localhost:8080
%test.RBAC_ENDPOINT=http://localhost:8080
```

### 3. Production Configuration

For stage/prod environments, configure PSK and other identity headers in `clowdapp.yaml` like below:

```yaml
envs:
  - name: SWATCH_SELF_PSK
    valueFrom:
      secretKeyRef:
        name: swatch-psks
        key: self

objects:
  - apiVersion: cloud.redhat.com/v1alpha1
    kind: ClowdApp
    metadata:
      name: swatch-contracts

    spec:
      dependencies:
      - rbac

  - apiVersion: v1
    kind: Secret
    metadata:
      name: swatch-psks
    data:
      self: <base64 encoded see swatch-contract clowdapp.yaml for details>

```

### 4. Configure OpenAPI Security

Add security definitions to your `openapi.yaml`:

```yaml
# Define security schemes
components:
   securitySchemes:
      customer:
         type: apiKey
         description: customer facing API
         name: x-rh-identity
         in: header
      support:
         type: apiKey
         description: API is available for support operations
         name: x-rh-identity
         in: header
      service:
         type: apiKey
         description: API is available for services
         name: x-rh-swatch-psk
         in: header
      test:
         type: apiKey
         description: API is available for testing purposes (pre-production environments
            only)
         name: x-rh-swatch-psk
         in: header

# Apply security to endpoints
paths:
  /api/v1/metrics:
    get:
      summary: Get metrics data
      security:
        - customer: [ ]
        - service: [ ]
      responses:
        '200':
          description: Metrics retrieved successfully
        '401':
          description: Authentication required
        '403':
          description: Insufficient permissions request forbidden
          $ref: "#/components/responses/Forbidden"
```

### 5. Secure Your Endpoints

```java
@Path("/api/v1/metrics")
@ApplicationScoped
public class MetricsResource {

    @GET
    @RolesAllowed({"customer", "service"})  // Both customer and service access
    public Response getMetrics() {
        // Your implementation
    }
}
```

## Available Roles

The security library assigns the following roles:

| Role |
|------|
| `customer` |
| `service` | 
| `test` | 
| `support` |

- **Service Role**
   - Purpose: Internal service-to-service authentication (swatch-billable-usage to swatch-contract service) or teamnado team calling our API using x509 cert
   - Granted to: `x-rh-swatch-psk` authenticated principals or X509 certificates
   - Environment: Mainly used in prod but is available for lower envs
   - Note: Identity header for psk and x509 doesn’t include org info because it can be a certificate or psk

- **Test Role**
   - Purpose: Test/development APIs and debugging endpoints like IQE in EE or local testing
   - Granted to: Any authenticated user (when enabled)
   - Environment: Only when `SWATCH_TEST_APIS_ENABLED=true` so used for local and IQE testing purposes and not prod.

- **Support Role**
   - Purpose: Internal Redhat support operations & customer assistance by Redhat employees
   - Granted to: `x-rh-identity` authenticated principals when type = `Associate`
   - Environment: Mainly used for prod but is available for lower envs

- **Customer Role**
   - Purpose: console.redhat.com UI or service account for self service access for customers
   - Granted to: Customer-authenticated accounts only using RBAC
   - Environment: Mainly used in prod

## Environment-Specific Configuration

### Development
- RBAC disabled (grants all roles)
- PSK from application.properties

### Test
- RBAC disabled (grants all roles)
- PSK from application.properties

### Stage/Production
- RBAC enabled (real permissions)
- PSK from Kubernetes secrets

## Security Best Practices

### 1. Endpoint Security
```java
// Good: Explicit role requirement
@RolesAllowed("customer")
public Response getData() { 
    //Do something 
}

// Bad: No security annotation
public Response getData() {
   //Do something
}
```

## Troubleshooting

### Common Issues

1. **401 Unauthorized**
   - Check authentication headers are present
   - Verify PSK matches configuration
   - Ensure x-rh-identity header is properly encoded

2. **403 Forbidden**
   - Verify user has required RBAC permissions
   - Check if RBAC service is accessible
   - Confirm role assignments in RBAC
