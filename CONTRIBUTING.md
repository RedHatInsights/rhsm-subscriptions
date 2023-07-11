Preferred Package Structure
---------------------------

* config 
* resource
  * http "entrypoints", extend resteasy Resource interfaces that are generated with openapi generator 
  * resource should have an injected service and not inject a repository class directly
* model
  * Dtos and other pojos 
* MapStruct mappers 
* exception 
  * extending Exception
* service 
  * Business logic, orchestration, things that aren't appropriate in the other packages 
  * entities should be converted to DTO in this layer prior to being returned to resource classes 
  * kafka producer and consumers live here 
  * umb communication lives here 
  * interact with the database by having a service class inject a repository class 
* repository 
  * PanacheRepository and JpaRepository 
  * JPA entities 
    * Class names should have "Entity" suffix to differentiate them from their DTO counterparts
* security
  * authentication, authorization, RBAC, etc.

DB migrations
-------------

We use [liquibase](https://docs.liquibase.com/) to manage our DB schema.

* `./gradlew :liquibaseUpdate` runs DB migrations
* `./gradlew :liquibaseRollbackCount -PliquibaseCommandValue=1` rolls back a single DB changeset

You can use a prefix to target different subprojects when they have liquibase enabled:

```shell
./gradlew swatch-contracts:liquibaseUpdate
```

There are various other tasks, see `./gradlew help tasks | grep liquibase`. Note that we don't use
Liquibase Hub or Liquibase Pro.

DB migrations should be written to roll back cleanly (exceptions should be discussed with the team).

-------------

# swatch-product-configuration

Directory structure: `$Platform/$Subscription_Name.yaml` (e.g. RHEL/RHEL_for_x86.yaml).

Use camel case for yaml properties that need to be serialized into the `com.redhat.swatch.configuration.registry` object definitions.
