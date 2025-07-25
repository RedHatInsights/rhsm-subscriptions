SHELL=/bin/bash

.PHONY: \
	swatch-contracts \
	swatch-billable-usage \
	swatch-producer-aws \
	swatch-producer-azure \
	swatch-tally \
	swatch-metrics-hbi \
	swatch-metrics \
	swatch-system-conduit \
	run-migrations \
	build

# Add a profile(s) to use like so:
# make swatch-contracts PROFILES=dev,other_profile
#
# Add the local profile automatically
override PROFILES+=dev

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
# The QUARKUS_CONFIG_PROFILE_PARENT setting is a little different from the
# SPRING_PROFILES_INCLUDE option that we use in the SPRING_PROXY define.
# QUARKUS_CONFIG_PROFILE_PARENT sets a fallback profile that will be consulted if
# the profiles set in QUARKUS_PROFILE do not have a property.  The quarkus:dev
# command is meant to use the dev profile by default, so it seems appropriate
# to set it as a fallback (and sparing users the annoyance of constantly adding
# PROFILES=dev to their make commands) while still providing some flexibility
# for additional profile manipulations.  If a dev absolutely doesn't want the dev
# profile loaded, they can unset the parent like so
# make swatch-metrics-hbi QUARKUS_PARENT_PROFILE=
# See https://quarkus.io/guides/config-reference#profiles
define QUARKUS_PROXY
    $(call BUILD,$(1))
	QUARKUS_HTTP_PORT=$(2) QUARKUS_MANAGEMENT_PORT=$(shell echo $$((1000 + $(2)))) \
	QUARKUS_HTTP_HOST=0.0.0.0 QUARKUS_PROFILE=$(subst $(space),$(comma),$(PROFILES)) \
	./mvnw -pl $(1) quarkus:dev
endef

# Take note that we're using SPRING_PROFILES_INCLUDE rather that
# SPRING_PROFILES_ACTIVE The INCLUDE directive will take the specified profiles
# and add them to the list of profiles in use.  In other words, it is additive
# to other methods of giving profiles such as through definitions in
# applications.properties.  The ACTIVE directive defines the totality of
# profiles that will be in use.  Our Spring profile organization is so complex
# that I don't want to force developers to have to remember the full listing
# of profiles they want to use.
define SPRING_PROXY
    $(call BUILD,$(1))
	SERVER_PORT=$(2) MANAGEMENT_SERVER_PORT=$(shell echo $$((1000 + $(2)))) \
	SPRING_PROFILES_INCLUDE=$(subst $(space),$(comma),$(PROFILES)) \
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

swatch-system-conduit:
	$(call SPRING_PROXY,$@,8017)