FROM registry.access.redhat.com/ubi8/openjdk-11:1.14
USER root
WORKDIR /tmp/src
ADD . /tmp/src

FROM registry.access.redhat.com/ubi8/openjdk-11:1.14
COPY --from=0 /tmp/src/build/libs/* /deployments/