SHELL=/bin/bash

.PHONY: swatch-contracts swatch-billable-usage swatch-producer-aws swatch-producer-azure swatch-metrics-hbi

# Add a profile(s) to use like so:
# make swatch-contracts PROFILES=dev,other_profile
#
# Add the wiremock profile automatically
override PROFILES+=wiremock

# See section 8.1 of the make manual
comma:=,
empty:=
space:=$(empty) $(empty)

# $1 is the directory with the application to start.
# $2 is the port number to start on.  The management port will be $2 + 1000
define QUARKUS_PROXY
	QUARKUS_HTTP_PORT=$(2) QUARKUS_MANAGEMENT_PORT=$(shell echo $$((1000 + $(2)))) \
	QUARKUS_HTTP_HOST=0.0.0.0 QUARKUS_PROFILE=$(subst $(space),$(comma),$(PROFILES)) \
	./mvnw -f $(1)/pom.xml quarkus:dev
endef

# $@ is a variable set to the target name
# If you add a new target here, be sure to add it to .PHONY at the top
# Otherwise, make will think the target name refers to the directory
swatch-contracts:
	$(call QUARKUS_PROXY,$@,8001)

swatch-billable-usage:
	$(call QUARKUS_PROXY,$@,8002)

swatch-producer-aws:
	$(call QUARKUS_PROXY,$@,8003)

swatch-producer-azure:
	$(call QUARKUS_PROXY,$@,8004)

swatch-metrics-hbi:
	$(call QUARKUS_PROXY,$@,8006)
