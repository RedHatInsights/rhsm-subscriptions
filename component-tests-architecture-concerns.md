# Component Tests Architecture Concerns - Team Discussion

## Background

During troubleshooting of the swatch-metrics-hbi component tests pipeline failure, we discovered architectural concerns about coupling between services through the Maven component-tests profile configuration.

## Issue Discovered

The swatch-metrics-hbi component tests were failing with:
```
Could not find artifact com.redhat.swatch:swatch-metrics-hbi:jar:1.1.0-SNAPSHOT
```

This occurred because:
1. The component tests import DTOs from the main swatch-metrics-hbi module (e.g., `HbiHostCreateUpdateEvent`, `Host`)
2. The component tests pom.xml already had a dependency on the main module
3. However, the Maven reactor build order in the component-tests profile didn't include the main module, so it wasn't built before the component tests ran

## Current Fix Applied

**Immediate Solution**: Added `swatch-metrics-hbi` main module to the component-tests profile in the root pom.xml to ensure proper build order.

**Concern**: This creates coupling between the test framework and the swatch-metrics-hbi module, making the module available to all other component tests.

## Architectural Concerns

### 1. Service Coupling Through Shared Dependencies

The component-tests profile currently includes shared modules that become available to all component tests:

```xml
<modules>
  <!-- Dependencies -->
  <module>swatch-quarkus-parent</module>
  <module>swatch-model-billable-usage</module>  <!-- Shared across all CT -->
  <!-- Test framework module -->
  <module>swatch-test-framework</module>
  <!-- Main modules needed by component tests -->
  <module>swatch-metrics-hbi</module>           <!-- Now also shared -->
  <!-- Services with component tests -->
  <module>swatch-producer-azure/ct</module>
  <module>swatch-tally/ct</module>
  <module>swatch-utilization/ct</module>
  <module>swatch-contracts/ct</module>
  <module>swatch-metrics-hbi/ct</module>
</modules>
```

### 2. Historical Context: swatch-model-billable-usage

**When Added**: Commit 31f347a4 by Jose Carvajal
**Purpose**: Original component testing framework setup for swatch-producer-azure
**Usage**: swatch-producer-azure component tests import billable usage classes:
- `BillableUsage`
- `BillableUsageAggregate`
- `BillableUsageAggregateKey`

This was added because the original component tests needed to work with billable usage concepts.

### 3. Inconsistent Patterns

**swatch-producer-azure**: Self-contained component tests that only depend on shared modules, not their main module

**swatch-metrics-hbi**: Component tests that import classes from their main module, requiring the main module as a dependency

## Discussion Points

### 1. Service Isolation vs. Shared Testing Infrastructure

**Question**: Should component tests for each service be completely isolated, or is it acceptable for them to share common dependencies through the Maven profile?

**Trade-offs**:
- **Isolation**: Better service boundaries, cleaner architecture
- **Sharing**: Reduced duplication, easier maintenance of common test utilities

### 2. Component Test Self-Containment

**Question**: Should component tests be self-contained (not depend on their main module) or is it acceptable for them to import production classes?

**Current State**:
- swatch-producer-azure: Self-contained ✓
- swatch-metrics-hbi: Depends on main module ✗

### 3. Shared Module Scope

**Question**: What criteria should determine if a module belongs in the shared component-tests profile?

**Current Shared Modules**:
- `swatch-model-billable-usage` - Domain model used by multiple services
- `swatch-metrics-hbi` - Service-specific module (concerning)

### 4. Future Architecture Direction

**Question**: As we move toward "each Maven module should be standalone", how should this affect component test architecture?

**Potential Approaches**:
1. **Standalone Component Tests**: Each service's component tests are completely self-contained
2. **Minimal Shared Infrastructure**: Only test framework and truly cross-cutting models are shared
3. **Service-Specific Profiles**: Each service has its own component test profile

## Recommendations for Discussion

1. **Audit Current Usage**: Review what classes each component test actually imports from shared modules
2. **Define Sharing Criteria**: Establish clear guidelines for what belongs in shared component test dependencies
3. **Refactor Strategy**: Develop a plan to move toward more isolated component tests
4. **Documentation**: Document the architectural decisions and patterns for component tests

## Action Items

- [ ] Team decision on acceptable level of coupling in component tests
- [ ] Review and potentially refactor swatch-metrics-hbi component tests to be self-contained
- [ ] Audit usage of swatch-model-billable-usage across all component tests
- [ ] Establish clear guidelines for component test dependencies
- [ ] Consider service-specific component test profiles as alternative architecture