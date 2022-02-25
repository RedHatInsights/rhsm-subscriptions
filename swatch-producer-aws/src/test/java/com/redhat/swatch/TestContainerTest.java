package com.redhat.swatch;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.Assert;
import org.junit.jupiter.api.Test;


@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(KafkaResource.class)
class TestContainerTest {

  @Test
  void testContainersStarting() {
    Assert.assertTrue(true);
  }
}