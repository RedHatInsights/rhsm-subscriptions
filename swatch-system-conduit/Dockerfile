FROM registry.access.redhat.com/ubi9/openjdk-17:1.18

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

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle dependencies.gradle ./
COPY buildSrc buildSrc
# The commented out commands are used for quarkus offline as we need the subprojects to run at top level
# Have to add too many files to have quarkus offline run
# We can revist once we refactor the codebases a bit

#COPY swatch-contracts/build.gradle swatch-contracts/build.gradle
#COPY swatch-producer-aws/build.gradle swatch-producer-aws/build.gradle
#COPY clients clients
#COPY clients-core/build.gradle clients-core/build.gradle
#COPY swatch-common-config-workaround/build.gradle swatch-common-config-workaround/build.gradle
#COPY swatch-common-resteasy/build.gradle swatch-common-resteasy/build.gradle
#COPY swatch-product-configuration/build.gradle swatch-product-configuration/build.gradle
#RUN ./gradlew quarkusGoOffline

COPY . .
ARG GRADLE_BUILD_ARGS=''
ARG GRADLE_TASKS='assemble'
RUN ./gradlew ${GRADLE_TASKS} -x test ${GRADLE_BUILD_ARGS}

# See https://stackoverflow.com/a/786515
RUN (cd /stage/swatch-system-conduit && exec jar -xf ./build/libs/*.jar)
RUN ls -al /stage/swatch-system-conduit

FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.20-2
USER root
RUN microdnf \
    --disablerepo=* \
    --enablerepo=ubi-9-baseos-rpms \
    install -y tar rsync

COPY --from=0 /stage/swatch-system-conduit/BOOT-INF/lib /deployments/lib
COPY --from=0 /stage/swatch-system-conduit/META-INF /deployments/META-INF
COPY --from=0 /stage/swatch-system-conduit/BOOT-INF/classes /deployments/

COPY --from=0 /stage/swatch-system-conduit/build/javaagent/* /opt/
RUN chmod -R g=u /deployments

USER default
ENV JAVA_MAIN_CLASS=org.candlepin.subscriptions.SystemConduitApplication
ENV JAVA_LIB_DIR=/deployments/lib/*
