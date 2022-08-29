FROM registry.access.redhat.com/ubi8/openjdk-11:1.14

COPY build/libs/* /deployments/
