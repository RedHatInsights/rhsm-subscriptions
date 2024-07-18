FROM registry.access.redhat.com/ubi9/openjdk-17:1.20-2.1721231695

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

RUN jar -xf ./build/libs/*.jar

FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.20-2.1719294801
USER root
RUN microdnf \
    --disablerepo=* \
    --enablerepo=ubi-9-baseos-rpms \
    install -y tar rsync

# TODO: Investigate layertools? See https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#container-images.efficient-images.layering
# and https://spring.io/guides/topicals/spring-boot-docker/#_spring_boot_layer_index
COPY --from=0 /stage/BOOT-INF/lib /deployments/lib
COPY --from=0 /stage/META-INF /deployments/META-INF
COPY --from=0 /stage/BOOT-INF/classes /deployments/

COPY --from=0 /stage/build/javaagent/* /opt/

# Required by Red Hat OpenShift Software Certification Policy Guide
COPY --from=0 /stage/LICENSE /licenses/

RUN chmod -R g=u /deployments

USER default
ENV JAVA_MAIN_CLASS=org.candlepin.subscriptions.BootApplication
ENV JAVA_LIB_DIR=/deployments/lib/*
