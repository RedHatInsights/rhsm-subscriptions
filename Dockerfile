ARG SWATCH_BUILD_TAG=latest
FROM swatch-build:${SWATCH_BUILD_TAG} AS swatch-build

FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.22-1.1745840591
USER root
RUN microdnf \
    --disablerepo=* \
    --enablerepo=ubi-9-baseos-rpms \
    install -y tar rsync
RUN microdnf \
  --disablerepo=* \
  --enablerepo=ubi-9-appstream-rpms \
  --enablerepo=ubi-9-baseos-rpms \
  update -y

# TODO: Investigate layertools? See https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#container-images.efficient-images.layering
# and https://spring.io/guides/topicals/spring-boot-docker/#_spring_boot_layer_index
COPY --from=swatch-build /stage/BOOT-INF/lib /deployments/lib
COPY --from=swatch-build /stage/META-INF /deployments/META-INF
COPY --from=swatch-build /stage/BOOT-INF/classes /deployments/

COPY --from=swatch-build /stage/build/javaagent/* /opt/

# Required by Red Hat OpenShift Software Certification Policy Guide
COPY --from=swatch-build /stage/LICENSE /licenses/

RUN chmod -R g=u /deployments

USER default

# Custom JVM properties:
## - Fix CVE-2024-31141: Disabling Kafka client config providers
## - OmitStackTraceInFastThrow: disabling the optimization that eliminates the full exception stack trace
ENV INTERNAL_OPTS_APPEND="-Dorg.apache.kafka.automatic.config.providers=none -XX:-OmitStackTraceInFastThrow"
ENV JAVA_OPTS_APPEND="$INTERNAL_OPTS_APPEND $USER_OPTS_APPEND"
ENV JAVA_MAIN_CLASS=org.candlepin.subscriptions.BootApplication
ENV JAVA_LIB_DIR=/deployments/lib/*
