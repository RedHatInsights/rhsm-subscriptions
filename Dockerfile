FROM registry.access.redhat.com/ubi8/openjdk-11:1.14

WORKDIR /opt
RUN curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
ENV JAVA_OPTS_APPEND=-javaagent:/opt/opentelemetry-javaagent.jar
COPY build/libs/* /deployments/
