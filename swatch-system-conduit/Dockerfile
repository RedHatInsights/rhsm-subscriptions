FROM registry.access.redhat.com/ubi8/openjdk-11
USER root

ADD --chown=jboss https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /opt/
ENV JAVA_OPTS_APPEND=-javaagent:/opt/opentelemetry-javaagent.jar
COPY build/libs/ /deployments/
