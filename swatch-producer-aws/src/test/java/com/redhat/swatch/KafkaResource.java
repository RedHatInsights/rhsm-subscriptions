package com.redhat.swatch;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaResource implements
    QuarkusTestResourceLifecycleManager {

  static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka"));


  @Override
  public Map<String, String> start() {
    kafka.start();
    return Collections.singletonMap(
        "kafka.bootstrap.servers", kafka.getBootstrapServers()
    );
  }

  @Override
  public void stop() {
    kafka.stop();
  }
}
