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
	swatch-utilization \
	swatch-api \
	component-test \
	build \
	format \
	install \
	test \
	clean \
	status

# Add a profile(s) to use like so:
# make swatch-contracts PROFILES=dev,other_profile
#
# Add the local profile automatically
override PROFILES+=dev

# Run mvnw in quiet mode by default. Use VERBOSE=true to see full output.
#
# The openapi-generator-maven-plugin prints a donation banner on every
# invocation, wrapped in lines of '#' characters.  There is no plugin-level
# option to suppress it (see https://github.com/OpenAPITools/openapi-generator/issues/11211).
# To filter it out without losing other output, MVN is defined as a shell
# wrapper that pipes all mvnw output through sed.  The sed expression uses
# [#] character classes instead of literal # characters because Make treats #
# as a comment delimiter even inside recipe quotes.  The HASH variable (below)
# holds a literal # character so it can be used in the sed expression via
# $(HASH) without triggering Make's comment parsing.  The sed range expression
# '/^####/,/^####/d' treats lines beginning with "####" as delimiters and
# deletes every line between and including those delimiters, which matches the
# banner block exactly.  The wrapper uses bash -c so that the pipe is part of
# the command itself; the trailing "--" sets $0, and "$$@" forwards all
# Make-supplied arguments (goals, flags, etc.) to mvnw unchanged.
MVN=bash -c './mvnw $(if $(VERBOSE),,-q) "$$@" 2>&1 | sed "/^$(HASH)$(HASH)$(HASH)$(HASH)/,/^$(HASH)$(HASH)$(HASH)$(HASH)/d"' --

# Init
HASH := \#
comma:=,
empty:=
space:=$(empty) $(empty)

# $1 is the directory with the application to start.
define BUILD
    $(if $(filter build,$(MAKECMDGOALS)),$(MVN) clean install -DskipTests -am -pl $(1))
endef

# $1 is the directory with the application to start.
# $2 is the port number to start on.  The management port will be $2 + 1000
# Pass SUSPEND_DEBUG=true to disable the JDWP debug agent.
define QUARKUS_PROXY
    $(call BUILD,$(1))
	QUARKUS_HTTP_PORT=$(2) QUARKUS_MANAGEMENT_PORT=$(shell echo $$((1000 + $(2)))) \
	QUARKUS_HTTP_HOST=0.0.0.0 QUARKUS_PROFILE=$(subst $(space),$(comma),$(PROFILES)) \
	$(MVN) -pl $(1) quarkus:dev -DskipTests $(if $(SUSPEND_DEBUG),-Ddebug=false)
endef

# Take note that we're using SPRING_PROFILES_INCLUDE rather that
# SPRING_PROFILES_ACTIVE. The INCLUDE directive will take the specified profiles
# and add them to the list of profiles in use.  In other words, it is additive
# to other methods of giving profiles such as through definitions in
# applications.properties.  The ACTIVE directive defines the totality of
# profiles that will be in use.  Our Spring profile organization is so complex
# that I don't want to force developers to have to remember the full listing
# of profiles they want to use.
# Pass SUSPEND_DEBUG=true to disable the debug agent.
DEBUG_ARGUMENT:=-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
define SPRING_PROXY
    $(call BUILD,$(1))
	SERVER_PORT=$(2) MANAGEMENT_SERVER_PORT=$(shell echo $$((1000 + $(2)))) \
	SPRING_PROFILES_INCLUDE=$(subst $(space),$(comma),$(PROFILES)) \
	$(MVN) -pl $(1) spring-boot:run -DskipTests \
	$(if $(SUSPEND_DEBUG),,$(DEBUG_ARGUMENT))
endef

# $1 is the directory with the application to start.
define COMPONENT_TEST_PROXY
	$(call BUILD,$(1))
	$(MVN) clean install -Pcomponent-tests -pl $(1)/ct -am
endef

default: format install

format:
	$(MVN) spotless:apply -Pbuild -Pcomponent-tests

clean:
	$(MVN) clean

# E.g. make install PL=swatch-core
install: clean
	$(MVN) install -DskipTests $(if $(PL),-pl $(PL) -am)

# E.g. make test PL=swatch-tally TEST=TallyRetentionControllerTest,TallySnapshotControllerTest
# The - tells make to continue the recipe even if the command failed (i.e. test failures)
# The default CSS misaligns the cell values and there's no good way to get the report-only
# task to update the CSS itself
test:
	-$(MVN) test $(if $(PL),-pl $(PL) -am) $(if $(TEST),-Dtest=$(TEST) -Dsurefire.failIfNoSpecifiedTests=false)
	@$(MVN) surefire-report:report-only && cp config/maven/site.css target/reports/css/
	@echo "View report at file://$$(git rev-parse --show-toplevel)/target/reports/surefire.html"

# Empty target for build flag
build:
	@:

COMPONENT_NAME := $(word 1,$(filter-out component-test build,$(MAKECMDGOALS)))

component-test:
	@if [ -z "$(COMPONENT_NAME)" ]; then \
		echo "Usage: make component-test <component-name>"; \
		exit 1; \
	fi
	$(call COMPONENT_TEST_PROXY,$(COMPONENT_NAME))

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

swatch-utilization:
	$(call QUARKUS_PROXY,$@,8018)

swatch-api:
	$(eval override PROFILES+=api)
	$(call SPRING_PROXY,swatch-tally,8019)

# $1 = service name, $2 = port
define CHECK_SERVICE_STATUS
	@GREEN=$$(tput setaf 2 2>/dev/null || echo ''); \
	RED=$$(tput setaf 1 2>/dev/null || echo ''); \
	RESET=$$(tput sgr0 2>/dev/null || echo ''); \
	printf "%-25s " "$(1) ($(2)):"; \
	if curl -s -f http://localhost:$(2)/health >/dev/null 2>&1; then \
		printf "$${GREEN}✓ Running$${RESET}\n"; \
	else \
		printf "$${RED}✗ Not running$${RESET}\n"; \
	fi
endef

status:
	@BOLD=$$(tput bold 2>/dev/null || echo ''); \
	RESET=$$(tput sgr0 2>/dev/null || echo ''); \
	echo "$${BOLD}Service Status:$${RESET}"
	$(call CHECK_SERVICE_STATUS,swatch-tally,9010)
	$(call CHECK_SERVICE_STATUS,swatch-contracts,9011)
	$(call CHECK_SERVICE_STATUS,swatch-billable-usage,9012)
	$(call CHECK_SERVICE_STATUS,swatch-producer-aws,9013)
	$(call CHECK_SERVICE_STATUS,swatch-producer-azure,9014)
	$(call CHECK_SERVICE_STATUS,swatch-metrics-hbi,9015)
	$(call CHECK_SERVICE_STATUS,swatch-metrics,9016)
	$(call CHECK_SERVICE_STATUS,swatch-system-conduit,9017)
	$(call CHECK_SERVICE_STATUS,swatch-utilization,9018)
	$(call CHECK_SERVICE_STATUS,swatch-api,9019)

# Prevent make from trying to build the component name as a target when used with component-test
# This must be at the end to override the real targets defined above
ifneq ($(filter component-test,$(MAKECMDGOALS)),)
$(foreach arg,$(filter-out component-test build,$(MAKECMDGOALS)),$(eval $(arg):;@:))
endif
