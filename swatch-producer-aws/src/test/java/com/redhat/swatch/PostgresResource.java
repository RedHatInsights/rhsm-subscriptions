package com.redhat.swatch;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class PostgresResource implements
    QuarkusTestResourceLifecycleManager {

  static PostgreSQLContainer<?> db =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres"))
          .withDatabaseName("rhsm-subscriptions")
          .withUsername("rhsm-subscriptions")
          .withPassword("rhsm-subscriptions");

  @Override
  public Map<String, String> start() {
    db.start();
    return Collections.singletonMap(
        "quarkus.datasource.jdbc.url", db.getJdbcUrl()
    );
  }

  @Override
  public void stop() {
    db.stop();
  }
}
