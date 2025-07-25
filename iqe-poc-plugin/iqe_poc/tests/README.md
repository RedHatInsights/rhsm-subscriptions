# POC Plugin Component Tests

This directory contains component tests for the IQE POC plugin. These tests are designed to run in ephemeral environments only and follow the standard IQE plugin testing patterns.

## Test Files

### `test_component_poc.py`
Basic component tests that verify:
- Plugin accessibility and basic functionality
- HTTP client integration
- User identity access
- Plugin method functionality

### `test_component_advanced.py`
Advanced component tests that verify:
- Comprehensive plugin functionality
- Error handling and resilience
- Performance characteristics
- Plugin lifecycle management

## Test Markers

All tests use the following pytest markers:
- `@pytest.mark.ephemeral` - Indicates tests run in ephemeral environments
- `@pytest.mark.ephemeral_only` - Ensures tests only run in ephemeral environments

## Running the Tests

To run these component tests:

```bash
# Run all component tests
iqe tests plugin poc -m "ephemeral and ephemeral_only"

# Run specific test file
iqe tests plugin poc -k "test_verify_poc_plugin_endpoints"

# Run with verbose output
iqe tests plugin poc -v -m "ephemeral and ephemeral_only"
```

## Test Structure

Each test follows the standard IQE plugin pattern:

1. **Metadata Documentation**: Comprehensive docstring with metadata including assignee, importance, requirements, test steps, and expected results
2. **Application Fixture**: Uses `poc_user_app` fixture for consistent application access
3. **Ephemeral Markers**: Ensures tests only run in ephemeral environments
4. **Comprehensive Assertions**: Validates plugin functionality thoroughly
5. **Error Handling**: Graceful handling of expected failures in POC environment

## Fixtures

- `poc_user_app`: Provides application instance configured for POC plugin testing
- `application`: Standard IQE application fixture

## Configuration

The plugin uses configuration from `conf/poc.default.yaml` for test settings and logging configuration. 