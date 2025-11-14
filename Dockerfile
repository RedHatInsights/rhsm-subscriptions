FROM registry.access.redhat.com/ubi9/openjdk-17:1.23-6.1762870924

USER root
# Add git, so that the build can determine the git hash
# Disable all repos except for ubi repos, as ubi repos don't require auth;
# this makes the container buildable without needing RHEL repos.
RUN microdnf \
  --disablerepo=* \
  --enablerepo=ubi-9-appstream-rpms \
  --enablerepo=ubi-9-baseos-rpms \
  install -y \
  git
WORKDIR /stage

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml ./

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -P download dependency:resolve-plugins dependency:resolve --fail-never
COPY . .
ARG MAVEN_BUILD_ARGS=''
ARG MAVEN_TASKS='clean package'
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw ${MAVEN_TASKS} -DskipTests ${MAVEN_BUILD_ARGS}

RUN (cd /stage/swatch-tally && exec jar -xf ./target/*.jar)
RUN ls -al /stage/swatch-tally

FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.23-6.1761163791
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
COPY --from=0 /stage/swatch-tally/BOOT-INF/lib /deployments/lib
COPY --from=0 /stage/swatch-tally/META-INF /deployments/META-INF
COPY --from=0 /stage/swatch-tally/BOOT-INF/classes /deployments/

COPY --from=0 /stage/swatch-tally/target/javaagent/* /opt/

# Required by Red Hat OpenShift Software Certification Policy Guide
COPY --from=0 /stage/LICENSE /licenses/

RUN chmod -R g=u /deployments

USER default
# Custom JVM properties:
## - Fix CVE-2024-31141: Disabling Kafka client config providers
## - OmitStackTraceInFastThrow: disabling the optimization that eliminates the full exception stack trace
ENV INTERNAL_OPTS_APPEND="-Dorg.apache.kafka.automatic.config.providers=none -XX:-OmitStackTraceInFastThrow"
ENV JAVA_OPTS_APPEND="$INTERNAL_OPTS_APPEND $USER_OPTS_APPEND"
ENV JAVA_MAIN_CLASS=org.candlepin.subscriptions.BootApplication
ENV JAVA_LIB_DIR=/deployments/lib/*
