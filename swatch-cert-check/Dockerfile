FROM registry.access.redhat.com/ubi9/toolbox:9.6-1760422567

USER root
WORKDIR /opt

COPY swatch-cert-check/cert-check.sh .
RUN chmod 755 /opt/cert-check.sh
