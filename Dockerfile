FROM registry.redhat.io/openjdk/openjdk-8-rhel8
WORKDIR /tmp/src
ADD . /tmp/src
RUN ./gradlew assemble

FROM registry.redhat.io/openjdk/openjdk-8-rhel8
COPY --from=0 /tmp/src/build/libs/* /deployments/
