FROM registry.access.redhat.com/ubi9/openjdk-17:1.14-2.1679391793

COPY build/libs/* /deployments/
