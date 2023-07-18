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
package org.candlepin.subscriptions.user;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.ws.rs.core.Response.Status;
import java.util.concurrent.atomic.AtomicInteger;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.classify.Classifier;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotFoundSubscriptionsExceptionClassifierTest {

  @TestConfiguration
  public static class RetryTestConfig {
    @Bean
    public RetryPolicy testDefaultPolicy() {
      // The number of attempts includes the initial try.
      return new MaxAttemptsRetryPolicy(3);
    }

    @Bean
    public Classifier<Throwable, RetryPolicy> testClassifier(RetryPolicy testDefaultPolicy) {
      return new NotFoundSubscriptionsExceptionClassifier(testDefaultPolicy);
    }

    @Bean
    public RetryPolicy testRetryPolicy(Classifier<Throwable, RetryPolicy> testClassifier) {
      ExceptionClassifierRetryPolicy exceptionClassifierRetryPolicy =
          new ExceptionClassifierRetryPolicy();
      exceptionClassifierRetryPolicy.setExceptionClassifier(testClassifier);
      return exceptionClassifierRetryPolicy;
    }

    @Bean
    @Qualifier("testRetry")
    public RetryTemplate testRetry(RetryPolicy testRetryPolicy) {
      return new RetryTemplateBuilder()
          .fixedBackoff(10) // 10 ms
          .customPolicy(testRetryPolicy)
          .build();
    }

    @Bean
    public RetryingThing retryingThing(RetryTemplate testRetry) {
      return new RetryingThing(testRetry);
    }
  }

  public static class RetryingThing {
    private final RetryTemplate template;
    private AtomicInteger noContentTries = new AtomicInteger(0);
    private AtomicInteger serverErrorTries = new AtomicInteger(0);

    public RetryingThing(RetryTemplate template) {
      this.template = template;
    }

    public void noContentStatus() {
      template.execute(
          ctx -> {
            noContentTries.incrementAndGet();
            throw new SubscriptionsException(
                ErrorCode.ACCOUNT_MISSING_ERROR, Status.NOT_FOUND, "Account not found", "");
          });
    }

    public void serverErrorStatus() {
      template.execute(
          ctx -> {
            serverErrorTries.incrementAndGet();
            throw new SubscriptionsException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                Status.INTERNAL_SERVER_ERROR,
                "Account not found",
                "");
          });
    }

    public AtomicInteger getNoContentTries() {
      return noContentTries;
    }

    public AtomicInteger getServerErrorTries() {
      return serverErrorTries;
    }
  }

  @Autowired RetryingThing retryingThing;

  @Test
  void testDoesNotRetryOn204() {
    assertThrows(SubscriptionsException.class, () -> retryingThing.noContentStatus());
    assertEquals(1, retryingThing.getNoContentTries().get());
  }

  @Test
  void testDoesRetryOnServerError() {
    assertThrows(SubscriptionsException.class, () -> retryingThing.serverErrorStatus());
    assertEquals(3, retryingThing.getServerErrorTries().get());
  }
}
