FROM registry.access.redhat.com/ubi9/ubi-minimal:9.6-1752587672

USER root

# Disable all repos except for ubi repos, as ubi repos don't require auth;
# this makes the container buildable without needing RHEL repos.
RUN microdnf \
  --disablerepo=* \
  --enablerepo=ubi-9-appstream-rpms \
  --enablerepo=ubi-9-baseos-rpms \
  install -y jq bash

RUN mkdir /unleash

WORKDIR /opt/unleash

COPY bin/import-features.sh .

# Alternate arrangement if we want the container to be a little more self-contained.  With
# this set-up user's wouldn't need to know the name of the script or anything; they'd just need to
# pass in the path of the import file if it differed from the default given with CMD
# ENTRYPOINT ["/bin/bash", "/opt/unleash/import-features.sh"]
# CMD ["/unleash/flags.json"]

CMD ["/bin/bash", "/opt/unleash/import-features.sh", "/unleash/flags.json"]
