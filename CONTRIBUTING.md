GitHub PR Labels
----------------
On creation a PR should be labelled as follows:

* `Dev` - needs developer review.
* `QE` - needs QE validation/review.
* `QE Unneeded` - does not need QE validation.

Additionally, the following labels are used to track PR status:

* `Dev/approved` - approved by all assigned developers.
* `QE/approved` - approved by all assigned QE members.
* `needs-update` - needs changes identified by Dev/QE review.
* `hold` - should not be merged for a reason that must be specified in a PR comment.
* `needs-rebase` - needs to incorporate changes in the target branch.

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
-------------
# Build

Requirements to build the repository are:
- Gradle
- Java 17
- Podman with socket listening (instructions [here](#podman-installation-instructions)) / or Docker installed

Once we have all the requirements properly installed and configured, we can build the repository by doing:

```shell
./gradle clean build
```

-------------
# Code Style

- Disable wildcard imports

To disable it in the IntelliJ IDEA editor, navigate to _Editor_ -> _Code Style_ -> _Java_ -> _Imports_
and set _Class count to use import with '\*'_ to `999`. Do the same with _Names count to use static import with '\*'_.

-------------
# Formatting

Before submitting pull requests, remember to format your changes using:

```shell
./gradlew spotlessApply
```

Otherwise, the CI will detect your changes are not formatted and reject your pull request.

-------------
# DB migrations

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

To generate a new changeset, you can use a helper script in `bin/`:

```shell
bin/liquibase-new-changeset.sh change set description
```

-------------
# swatch-product-configuration

Directory structure: `$Platform/$Subscription_Name.yaml` (e.g. RHEL/RHEL_for_x86.yaml).

Use camel case for yaml properties that need to be serialized into the `com.redhat.swatch.configuration.registry` object definitions.

The `includedSubscriptions` property only applies to metrics that aren't sourced from prometheus.  It's used to prevent double counting capacity.

-------------
# Podman installation instructions

Since we're using Testcontainers to set up services during test execution, besides installing podman, you also need to enable podman socket with 
Docker REST API.

First, you need to install podman. For Linux systems, you need to do:

```shell
sudo dnf install podman podman-docker
```

In other systems, follow [the official site guide](https://podman.io/docs/installation) to install podman.

Once installed, you need to enable the podman socket with Docker REST API with systemd (only needs to be done once):

```shell
systemctl --user enable podman.socket --now
```

In systems when systemd is not running (WSL etc), instead of the above command, you need to do:

```shell
podman system service --time=0
```

Then, you can obtain the path of the socket with the following command:

```shell
$ podman info | grep -A2 'remoteSocket'

remoteSocket:
  path: /path/to/podman.sock
```

Finally, you need to configure the DOCKER_HOST environment property, so Testcontainers use it:

```shell
export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
```

-------------
# Spring Boot to Quarkus migration tips

This section aims to add some tips of known differences between Spring Boot and Quarkus, and how to migrate it.

### **kafka**: `KafkaTemplate.send` (Spring Boot) is not the same than `Emitter.send` (Quarkus)

The `KafkaTemplate.send` method is blocking where `Emitter.send` is not. 
Actually, if you don't handle the response from `Emitter.send`, the message might not be sent.
The correct equivalent method in Quarkus is `MutinyEmitter.sendAndWait` and it needs to be used by a method annotated with `@Blocking`. 

### **kafka**: does not support chain transactions with Hibernate ORM

Related to https://github.com/quarkusio/quarkus/issues/40530
An alternative solution is to use [the Outbox Pattern](https://spring.io/blog/2023/10/24/a-use-case-for-transactions-adapting-to-transactional-outbox-pattern), where we first insert the data into the database in a single 
transaction, then query the data again and send the message. If the message fails to be sent, we delete the created 
record in database. 
Example:

```java
@ApplicationScoped
public class NewAuthorConsumer {

    @Channel("new-book-out")
    Emitter<String> emitter;
    @Inject AuthorService authorService;

    @Incoming("new-author-in")
    Uni<Void> consume(Message<String> message) {
        // db tx 1 to create the author
        var id = authorService.handleAuthor(message.getPayload());
        // db tx 2 to query the author (this is just an example)
        var name = authorService.getAuthorName(id);
        return emitter.send(name).onFailure()
            .recoverWithUni(ex -> {
                 // failed to send
                 // db tx 3 to delete the author
                 authorService.deleteAuthor(id);
                 message.nack(ex);
            })
            .onItem().transformToUni(unused -> message.ack());
    }
}
```

### **data**: the repository `save` method (Spring Boot) is not the same than the panache `persist` method (Quarkus)

The repository `save` method (Spring Boot) is doing much more than the panache `persist` method (Quarkus) which is the standard JPA operation:

```java
@Transactional
public <S extends T> S save(S entity) {
    Assert.notNull(entity, "Entity must not be null");
    if (this.entityInformation.isNew(entity)) {
        this.entityManager.persist(entity);
        return entity;
    } else {
        return this.entityManager.merge(entity);
    }
}
```

When using the panache `persist` method:
- If the entity is attached to the persistence context (it was loaded using `find` or similar), then it will update this entity
- if the entity is not attached to the persistence context, then it will insert this record into the database

Therefore, when we create an entity with an identifier that already exists in database:

```java
Offering entity = new Offering("MW0001"); // where "MW0001" already exists on database
entity.setDescription("update this field");

// then, in Spring:
offeringRepository.save(entity); // this will work ok and the existing record will be updated
// but, in Quarkus:
offeringRepository.persist(entity); // this will cause a ConstraintViolationException when the transaction ends because a new entity was inserted (with the same primary key)
```

The correct approach would be:

```java
Offering entity = offeringRepository.findByIdOptional("MW0001").orElseGet(() -> new Offering("MW0001"));
offeringRepository.persist(entity);
```

Note that it's also possible to use the `merge` operation in Quarkus, so the entity is updated if it does exist or creates it.
However, the use of `merge` is not recommended as it brings some drawbacks like it does not work well when the entity uses autogenerated identifiers.

### **data**: logging queries

In Quarkus, we need to add:

```
quarkus.hibernate-orm.log.sql=${LOGGING_SHOW_SQL_QUERIES:false}
```

Where "LOGGING_SHOW_SQL_QUERIES" is the common property that we use for all our services (Spring Boot and Quarkus services).

### **rest-client**: disable the provider auto-discovery

Since having multiple clients in the classpath will cause that the exception mappers for all of these clients will be configured for all.

```
# Disable the auto-discovery of providers (for ex: exception mappers).
# All the clients must use either the "quarkus.rest-client.*.providers" property or the `@RegisterProvider` instead.
quarkus.rest-client-reactive.provider-autodiscovery=false
```

The generated client from openapi already uses the `@RegisterProdiver` annotation, so nothing else should be needed.

### **rest-client**: will throw an ProcessingException exception under some circumstances

So, catch the ProcessingException exception as well as the ApiException one (for generated clients) to be sure, you catch all the errors.

### **fault-tolerance**: Quarkus does not have a configurable exponential backoff retry strategy

In Spring Boot, we created a RetryTemplate bean where we could configure it using some properties:

```java
@Bean
public RetryTemplate subscriptionServiceRetryTemplate(
    ApplicationProperties applicationProperties) {

  return new RetryTemplateBuilder()
      .maxAttempts(applicationProperties.getSubscription().getMaxRetryAttempts())
      .exponentialBackoff(
          applicationProperties.getSubscription().getBackOffInitialInterval().toMillis(),
          applicationProperties.getSubscription().getBackOffMultiplier(),
          applicationProperties.getSubscription().getBackOffMaxInterval().toMillis())
      .notRetryOn(UnretryableException.class)
      .build();
}
```

In Quarkus, the official way to retry operations in Quarkus is described in [here](https://quarkus.io/guides/smallrye-fault-tolerance#adding-resiliency-retries).
Where we can also configure it to use an exponential backoff strategy, for example:

```java
public class CoffeeResource {
    ...
    @GET
    @Retry(maxRetries = 4)
    @ExponentialBackoff(factor = 2, maxDelay = 10000, maxDelayUnit = ChronoUnit.MILLIS)
    public List<Coffee> coffees() {
        ...
    }
    ...
}
```

Unfortunately, this brings some limitations:

- We can't configure the `@Retry` and `@ExponentialBackoff` annotations using the properties
- The Smallrye programmatically API could be used, but we would need to alter our logic to use it

Therefore, we have built our custom `@RetryWithExponentialBackoff` annotation to address these limitations:

```java
  @RetryWithExponentialBackoff(
      maxRetries = "${SUBSCRIPTION_MAX_RETRY_ATTEMPTS:4}",
      delay = "${SUBSCRIPTION_BACK_OFF_INITIAL_INTERVAL:1000ms}",
      maxDelay = "${SUBSCRIPTION_BACK_OFF_MAX_INTERVAL:64s}",
      factor = "${SUBSCRIPTION_BACK_OFF_MULTIPLIER:2}")
  public List<Subscription> getSubscriptionsByOrgId(String orgId, int index, int pageSize) {
     ...
  }
```

To use this annotation `@RetryWithExponentialBackoff`, you need to import the library `swatch-common-smallrye-fault-tolerance`.

### **micrometer**: out of memory exceptions in tests

Quarkus brings a known issue (see [here](https://github.com/quarkusio/quarkus/issues/24210#issuecomment-1064833013)) when running tests with micrometer enabled causing out of memory issues while also using the QuarkusTestResource implementations.

To workaround this issue, we need to disable micrometer in tests by adding the following properties:

```
%test.quarkus.micrometer.binder.jvm=false
%test.quarkus.micrometer.binder.http-client.enabled=false
%test.quarkus.micrometer.binder.http-server.enabled=false
%test.quarkus.micrometer.binder.messaging.enabled=false
%test.quarkus.micrometer.binder.mp-metrics.enabled=false
```