FROM registry.access.redhat.com/ubi8/openjdk-11
USER root
WORKDIR /tmp/src
ADD . /tmp/src
RUN ./mvnw install -DskipTests

FROM registry.access.redhat.com/ubi8/openjdk-11
COPY --from=0 /tmp/src/target/demo-0.0.1-SNAPSHOT.jar /deployments/
