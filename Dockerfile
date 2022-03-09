FROM registry.access.redhat.com/ubi8/openjdk-11
USER root
WORKDIR /tmp/src
ADD . /tmp/src
RUN ./gradlew :assemble

FROM registry.access.redhat.com/ubi8/openjdk-11
COPY --from=0 /tmp/src/build/libs/* /deployments/
