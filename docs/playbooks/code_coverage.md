# Code Coverage Report playbook

This guide will introduce how to generate and produce the code coverage report at runtime for Spring Boot and Quarkus services.

## Set Up
- Dependant services: instructions [here](../../README.md#dependent-services).
- perform database migrations: `./gradlew liquibaseUpdate --no-daemon`
- Wiremock (for prometheus): instructions [here](../../README.md#wiremock-service).
- build all the swatch services: `./gradlew clean build -x test`
- setup jacoco javaagent:
```
export JAVA_OPTS_APPEND="-javaagent:$(pwd)/build/javaagent/jacoco-agent.jar=port=6300,address=0.0.0.0,destfile=jacoco.exec,includes=*,exclclassloader=*QuarkusClassLoader,append=true,output=tcpserver"
```
Where:
* `-javaagent`: Tells the JVM to run JaCoCo as an agent alongside your application.
* `build/javaagent/jacoco-agent.jar`: The path to the JaCoCo agent. This library is automatically downloaded when running the Gradle build.
* `port=6300`: JaCoCo listens on port 6300 for requests to dump coverage data.
* `address=0.0.0.0`: Sets the address that the JaCoCo agent will bind to.
* `destfile=jacoco.exec`: Specifies the file where coverage data will be stored.
* `includes=*`: Limits coverage to specific packages (in this case, *).
* `exclclassloader=*QuarkusClassLoader`: A list of class loader names, that should be excluded from execution analysis.
* `append=true`: Appends new coverage data to the existing file.
* `output=tcpserver`: Specifies that JaCoCo will be available via a TCP server, allowing you to dump coverage data on demand.

- download jacoco cli:
```
mkdir code-coverage
curl -o code-coverage/jacoco.jar https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.12/org.jacoco.cli-0.8.12-nodeps.jar
```

## Run SWATCH Services

- **swatch-metrics**: port 8002

```
cd swatch-metrics
java $JAVA_OPTS_APPEND -DSERVER_PORT=8002 -DQUARKUS_MANAGEMENT_PORT=9002 -DSWATCH_SELF_PSK=placeholder -DEVENT_SOURCE=telemeter -DPROM_URL=http://localhost:8101/api/v1/ -DENABLE_SPLUNK_HEC=false -jar build/quarkus-app/quarkus-run.jar 
```

## Generate report

```
java -jar code-coverage/jacoco.jar dump --address localhost --port 6300 --destfile code-coverage/services/jacoco-it.exec
```