FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.16-1.1693315923
USER root

COPY build/libs/ /deployments/
COPY build/javaagent/ /opt/
ENV JAVA_OPTS_APPEND=-javaagent:/opt/splunk-otel-javaagent.jar
