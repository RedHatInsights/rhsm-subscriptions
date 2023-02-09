FROM registry.access.redhat.com/ubi9/openjdk-17:1.13-12.1675790156

COPY build/libs/* /deployments/
