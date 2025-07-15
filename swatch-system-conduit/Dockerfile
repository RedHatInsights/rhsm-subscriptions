FROM registry.access.redhat.com/ubi9/openjdk-17:1.22-1.1752066189

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

# See https://stackoverflow.com/a/786515
RUN (cd /stage/swatch-system-conduit && exec jar -xf ./target/*.jar)
RUN ls -al /stage/swatch-system-conduit

FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.22-1.1752066188
USER root
RUN microdnf \
    --disablerepo=* \
    --enablerepo=ubi-9-baseos-rpms \
    install -y tar rsync

COPY --from=0 /stage/swatch-system-conduit/BOOT-INF/lib /deployments/lib
COPY --from=0 /stage/swatch-system-conduit/META-INF /deployments/META-INF
COPY --from=0 /stage/swatch-system-conduit/BOOT-INF/classes /deployments/

COPY --from=0 /stage/swatch-system-conduit/target/javaagent/* /opt/
RUN chmod -R g=u /deployments

USER default
# Custom JVM properties
## - Fix CVE-2024-31141: Disabling Kafka client config providers
## - OmitStackTraceInFastThrow: disabling the optimization that eliminates the full exception stack trace
ENV INTERNAL_OPTS_APPEND="-Dorg.apache.kafka.automatic.config.providers=none -XX:-OmitStackTraceInFastThrow"
ENV JAVA_OPTS_APPEND="$INTERNAL_OPTS_APPEND $USER_OPTS_APPEND"
ENV JAVA_MAIN_CLASS=org.candlepin.subscriptions.SystemConduitApplication
ENV JAVA_LIB_DIR=/deployments/lib/*
