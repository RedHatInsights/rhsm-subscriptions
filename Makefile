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
	run-migrations \
	build \
	format \
	install \
	clean \
	rollback \
	status

# Add a profile(s) to use like so:
# make swatch-contracts PROFILES=dev,other_profile
#
# Add the local profile automatically
override PROFILES+=dev

# Init
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
	./mvnw -pl $(1) quarkus:dev -DskipTests
endef

# Take note that we're using SPRING_PROFILES_INCLUDE rather that
# SPRING_PROFILES_ACTIVE. The INCLUDE directive will take the specified profiles
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
	./mvnw -pl $(1) spring-boot:run -DskipTests
endef

default: format install

format:
	./mvnw spotless:apply -Pbuild -Pcomponent-tests -Pcomponent-tests-by-service

clean:
	./mvnw clean

install: clean
	./mvnw install -DskipTests

# $@ is a variable set to the target name
# If you add a new target here, be sure to add it to .PHONY at the top
# Otherwise, make will think the target name refers to the directory
run-migrations:
	./mvnw install -Prun-migrations

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

swatch-utilization:
	$(call QUARKUS_PROXY,$@,8018)

swatch-api:
	$(eval override PROFILES+=api)
	$(call SPRING_PROXY,swatch-tally,8019)

rollback:
	@echo "Select database context:"
	@echo "1. Core context"
	@echo "2. Contracts context"
	@read -p "Enter context choice (1-2): " context; \
	case $$context in \
		1) CONTEXT="core" ;; \
		2) CONTEXT="contracts" ;; \
		*) echo "Invalid context choice"; exit 1 ;; \
	esac; \
	read -p "Enter number of changesets: " count; \
	./mvnw -f swatch-database/pom.xml exec:java -Dexec.args="$$CONTEXT rollbackCountSql --count=$$count"

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
