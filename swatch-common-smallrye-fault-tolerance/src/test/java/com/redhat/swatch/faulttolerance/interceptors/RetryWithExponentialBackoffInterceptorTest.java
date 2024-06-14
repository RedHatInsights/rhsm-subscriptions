/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.faulttolerance.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(
    value = RetryWithExponentialBackoffInterceptorTest.RuntimeProperties.class,
    restrictToAnnotatedClass = true)
class RetryWithExponentialBackoffInterceptorTest {
  private static final AtomicInteger COUNTER = new AtomicInteger(0);

  @Inject Service service;

  @BeforeEach
  void setUp() {
    COUNTER.set(0);
  }

  @Test
  void testNoBindingsThenOnlyOneExecution() {
    assertThrows(RuntimeException.class, () -> service.runUsingNoAnnotation());
    assertEquals(1, COUNTER.get());
  }

  @Test
  void testAnnotationUsingPlainValues() {
    assertThrows(RuntimeException.class, () -> service.runUsingDirectValue());
    assertEquals(4, COUNTER.get());
  }

  @Test
  void testAnnotationUsingComputedProperty() {
    assertThrows(RuntimeException.class, () -> service.runUsingNonExistingProperty());
    // using default property from the `@Retry` annotation
    assertEquals(4, COUNTER.get());
  }

  @Test
  void testAnnotationUsingComputedPropertyWithDefault() {
    assertThrows(RuntimeException.class, () -> service.runUsingNonExistingPropertyWithDefault());
    assertEquals(3, COUNTER.get());
  }

  @Test
  void testAnnotationUsingNestedComputedProperty() {
    assertThrows(
        RuntimeException.class, () -> service.runUsingNonExistingNestedPropertyWithDefault());
    assertEquals(6, COUNTER.get());
  }

  @Test
  void testAnnotationUsingExistingProperty() {
    assertThrows(RuntimeException.class, () -> service.runUsingExistingProperty());
    assertEquals(2, COUNTER.get());
  }

  @ApplicationScoped
  static class Service {

    void runUsingNoAnnotation() {
      run();
    }

    @RetryWithExponentialBackoff(maxRetries = "3")
    void runUsingDirectValue() {
      run();
    }

    @RetryWithExponentialBackoff(maxRetries = "${non-exist}")
    void runUsingNonExistingProperty() {
      run();
    }

    @RetryWithExponentialBackoff(maxRetries = "${non-exist:2}")
    void runUsingNonExistingPropertyWithDefault() {
      run();
    }

    @RetryWithExponentialBackoff(maxRetries = "${non-exist:${another-non-exist:5}}")
    void runUsingNonExistingNestedPropertyWithDefault() {
      run();
    }

    @RetryWithExponentialBackoff(maxRetries = "${existing-property:20}", delay = "100ms")
    void runUsingExistingProperty() {
      run();
    }

    void run() {
      COUNTER.incrementAndGet();
      throw new RuntimeException("Boom!");
    }
  }

  public static class RuntimeProperties implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
      return Map.of("existing-property", "1");
    }

    @Override
    public void stop() {}
  }
}
