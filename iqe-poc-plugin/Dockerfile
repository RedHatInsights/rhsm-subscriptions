# Multi-stage Docker build for IQE (Integration Quality Engineering) plugin
# This Dockerfile creates a containerized environment for running IQE tests
# with a custom plugin installed.

# STAGE 1: Builder stage
# Use the latest IQE core image as the base for building the plugin
# hadolint ignore=DL3007 - Using latest tag is acceptable for this use case
FROM quay.io/cloudservices/iqe-core:latest as builder

# Switch to root user to perform installation tasks
# hadolint ignore=DL3002 - Root access needed for plugin installation
USER 0

# Set working directory for plugin installation
WORKDIR /iqe_plugin

# Copy all plugin source code and configuration files into the container
COPY . .

# Install the IQE plugin and clean up afterwards
# - Install plugin with update and debug flags for verbose output
# - Remove pip cache to reduce image size
# - Set group write permissions on IQE virtual environment for compatibility
# hadolint ignore=SC2086 - Variable expansion is intentional here
RUN iqe plugin install --update --debug . && \
    rm -rf ${HOME}/.cache/pip && \
    chmod -R g+rwX ${IQE_VENV}

# STAGE 2: Final runtime image
# Create a clean runtime image with only the installed plugin
# hadolint ignore=DL3007 - Using latest tag is acceptable for this use case
FROM quay.io/cloudservices/iqe-core:latest

# Copy the installed plugin and virtual environment from builder stage
# This includes all dependencies and the plugin itself
COPY --from=builder ${IQE_VENV} ${IQE_VENV}

# Switch to non-root user for security best practices
# User 1001 is the standard non-root user in the IQE core image
USER 1001
