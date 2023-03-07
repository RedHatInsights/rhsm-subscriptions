FROM registry.access.redhat.com/ubi9/openjdk-17:1.14-2

COPY build/libs/* /deployments/
