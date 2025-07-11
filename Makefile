SHELL=/bin/bash

.PHONY: \
	swatch-contracts \
	swatch-billable-usage \
	swatch-producer-aws \
	swatch-producer-azure \
	swatch-tally \
	swatch-metrics-hbi \
	swatch-metrics \
	run-migrations \
	build

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
define BUILD
    $(if $(filter build,$(MAKECMDGOALS)),./mvnw clean install -DskipTests -am -pl $(1))
endef

# $1 is the directory with the application to start.
# $2 is the port number to start on.  The management port will be $2 + 1000
define QUARKUS_PROXY
    $(call BUILD,$(1))
	QUARKUS_HTTP_PORT=$(2) QUARKUS_MANAGEMENT_PORT=$(shell echo $$((1000 + $(2)))) \
	QUARKUS_HTTP_HOST=0.0.0.0 QUARKUS_PROFILE=$(subst $(space),$(comma),$(PROFILES)) \
	./mvnw -pl $(1) quarkus:dev
endef

define SPRING_PROXY
    $(call BUILD,$(1))
	SERVER_PORT=$(2) MANAGEMENT_SERVER_PORT=$(shell echo $$((1000 + $(2)))) \
	SPRING_PROFILES_ACTIVE=$(subst $(space),$(comma),$(PROFILES)) \
	./mvnw -pl $(1) spring-boot:run
endef

# $@ is a variable set to the target name
# If you add a new target here, be sure to add it to .PHONY at the top
# Otherwise, make will think the target name refers to the directory
run-migrations:
	./mvnw clean install -Prun-migrations
# Empty target for build flag
build:
	@:
swatch-tally:
	$(call SPRING_PROXY,$@,8010)

swatch-contracts:
	$(call QUARKUS_PROXY,$@,8011)

swatch-billable-usage:
	$(call QUARKUS_PROXY,$@,8012)

swatch-producer-aws:
	AWS_SHARED_CREDENTIALS_FILE=$(CURDIR)/config/moto/local-config.ini $(call QUARKUS_PROXY,$@,8013)

swatch-producer-azure:
	$(call QUARKUS_PROXY,$@,8014)

swatch-metrics-hbi:
	$(call QUARKUS_PROXY,$@,8015)

swatch-metrics:
	$(call QUARKUS_PROXY,$@,8016)
