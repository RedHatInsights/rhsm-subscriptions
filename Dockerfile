FROM registry.access.redhat.com/ubi9/openjdk-17:1.13-10.1669632202

COPY build/libs/* /deployments/
