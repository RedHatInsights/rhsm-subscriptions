FROM registry.access.redhat.com/ubi9/openjdk-21:1.24-2.1777575906

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
ENV HERMETO_GENERIC_DIR="/cachi2/output/deps/generic"

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml ./

# Hermeto generic prefetch: deps are under /cachi2/output/deps/generic/ with Maven
# repository layout (see out/artifacts.lock.yaml). Copy into
# the build-local repo and install prefetched Maven so the build does not hit the network.
RUN if [ ! -d "${HERMETO_GENERIC_DIR}" ] || ! ls -A "${HERMETO_GENERIC_DIR}" >/dev/null 2>&1; then echo "ERROR: ${HERMETO_GENERIC_DIR} is missing or empty; Hermeto prefetch is required." >&2; exit 1; fi; mkdir -p /root/.m2/repository; cp -a "${HERMETO_GENERIC_DIR}/." /root/.m2/repository/; DIST_URL="$(sed -n 's/^distributionUrl=//p' .mvn/wrapper/maven-wrapper.properties)"; DIST_REL="${DIST_URL#*maven2/}"; MAVEN_ZIP="/root/.m2/repository/${DIST_REL}"; if [ ! -f "${MAVEN_ZIP}" ]; then echo "ERROR: Prefetched Maven distribution not found at ${MAVEN_ZIP}." >&2; exit 1; fi; MAVEN_DIR="$(basename "${MAVEN_ZIP}" -bin.zip)"; mkdir -p /opt && cd /opt && jar xf "${MAVEN_ZIP}" && ln -sfn "/opt/${MAVEN_DIR}" /opt/apache-maven
ENV PATH="/opt/apache-maven/bin:${PATH}"
ENV MVN_COMMON_ARGS="-B -o"

# TEMPORARY WORKAROUND: Removed --mount=type=cache due to runc incompatibility
# See: https://issues.redhat.com/browse/APPSRE-13062
# This makes builds slower but allows them to work with runc until infra configures crun
# TODO: Re-enable cache mounts once infra switches to crun
RUN mvn ${MVN_COMMON_ARGS} -P download dependency:resolve-plugins dependency:resolve --fail-never
COPY . .
ARG VERSION=1.0.0
ARG MAVEN_BUILD_ARGS=''
ARG MAVEN_TASKS='clean package'
# TEMPORARY WORKAROUND: Removed --mount=type=cache due to runc incompatibility
RUN mvn ${MVN_COMMON_ARGS} ${MAVEN_TASKS} -pl swatch-system-conduit -am -DskipTests ${MAVEN_BUILD_ARGS}

# See https://stackoverflow.com/a/786515
RUN (cd /stage/swatch-system-conduit && exec jar -xf ./target/*.jar)
RUN ls -al /stage/swatch-system-conduit

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24-2.1777575919

ARG VERSION=1.0.0

# Required labels for Enterprise Contract
LABEL name="swatch-system-conduit"
LABEL maintainer="Red Hat, Inc."
LABEL version="ubi9"
LABEL release="${VERSION}"
LABEL vendor="Red Hat, Inc."
LABEL url="https://github.com/RedHatInsights/rhsm-subscriptions"
LABEL com.redhat.component="swatch-system-conduit"
LABEL distribution-scope="public"
LABEL io.k8s.description="SWATCH System Conduit service based on UBI9 OpenJDK 21."
LABEL description="SWATCH System Conduit service based on UBI9 OpenJDK 21."

#label for EULA
LABEL com.redhat.license_terms="https://www.redhat.com/en/about/red-hat-end-user-license-agreements#UBI"

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
