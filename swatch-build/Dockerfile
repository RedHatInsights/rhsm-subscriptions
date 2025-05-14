FROM registry.access.redhat.com/ubi9/openjdk-17:1.22-1.1745840591

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

COPY . .
ARG GRADLE_BUILD_ARGS=''
ARG GRADLE_TASKS='assemble'
RUN ./gradlew ${GRADLE_TASKS} -x test ${GRADLE_BUILD_ARGS} --parallel

RUN jar -xf ./build/libs/*.jar
RUN (cd /stage/swatch-system-conduit && exec jar -xf ./build/libs/*.jar)
