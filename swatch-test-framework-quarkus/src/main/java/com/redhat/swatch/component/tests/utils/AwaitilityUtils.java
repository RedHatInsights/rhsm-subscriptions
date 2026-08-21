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
package com.redhat.swatch.component.tests.utils;

import com.redhat.swatch.component.tests.logging.Log;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionEvaluationListener;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.EvaluatedCondition;
import org.awaitility.core.ThrowingRunnable;
import org.awaitility.core.TimeoutEvent;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

/** Awaitility utils to make a long or repeatable operation. */
public final class AwaitilityUtils {

  private AwaitilityUtils() {}

  /**
   * Wait until supplier returns false.
   *
   * @param supplier method to return the instance.
   */
  @SuppressWarnings("unchecked")
  public static void untilIsFalse(Callable<Boolean> supplier) {
    untilIsFalse(supplier, AwaitilitySettings.defaults());
  }

  /**
   * Wait until supplier returns false.
   *
   * @param supplier method to return the instance.
   */
  @SuppressWarnings("unchecked")
  public static void untilIsFalse(Callable<Boolean> supplier, AwaitilitySettings settings) {
    awaits(settings).until(() -> !supplier.call());
  }

  /**
   * Wait until supplier returns true.
   *
   * @param supplier method to return the instance.
   */
  @SuppressWarnings("unchecked")
  public static void untilIsTrue(Callable<Boolean> supplier) {
    untilIsTrue(supplier, AwaitilitySettings.defaults());
  }

  /**
   * Wait until supplier returns true.
   *
   * @param supplier method to return the instance.
   */
  @SuppressWarnings("unchecked")
  public static void untilIsTrue(Callable<Boolean> supplier, AwaitilitySettings settings) {
    awaits(settings).until(supplier);
  }

  /**
   * Wait until supplier returns a not null instance.
   *
   * @param supplier method to return the instance.
   * @return the non null instance.
   */
  @SuppressWarnings("unchecked")
  public static <T> T untilIsNotNull(Supplier<T> supplier) {
    return untilIsNotNull(supplier, AwaitilitySettings.defaults());
  }

  /**
   * Wait until supplier returns a not null instance.
   *
   * @param supplier method to return the instance.
   * @param settings Awaitility Settings
   * @return the non null instance.
   */
  @SuppressWarnings("unchecked")
  public static <T> T untilIsNotNull(Supplier<T> supplier, AwaitilitySettings settings) {
    return until(supplier, (Matcher<T>) Matchers.<T>notNullValue(), settings);
  }

  /**
   * Wait until supplier returns a not empty array.
   *
   * @param supplier method to return the instance.
   * @return the non empty array.
   */
  public static <T> T[] untilIsNotEmpty(Supplier<T[]> supplier) {
    return until(supplier, Matchers.arrayWithSize(Matchers.greaterThan(0)));
  }

  /**
   * Wait until the supplier returns an instance that satisfies the asserts.
   *
   * @param supplier method to return the instance.
   * @param asserts custom assertions that the instance must satisfy.
   */
  public static <T> void untilAsserted(Supplier<T> supplier, Consumer<T> asserts) {
    awaits().untilAsserted(() -> asserts.accept(get(supplier).call()));
  }

  /**
   * Wait until the assertions are satified.
   *
   * @param assertion custom assertions that the instance must satisfy.
   */
  public static void untilAsserted(ThrowingRunnable assertion) {
    untilAsserted(assertion, AwaitilitySettings.defaults());
  }

  /**
   * Wait until the assertions are satified.
   *
   * @param assertion custom assertions that the instance must satisfy.
   * @param settings Awaitility Settings
   */
  public static void untilAsserted(ThrowingRunnable assertion, AwaitilitySettings settings) {
    awaits(settings).untilAsserted(assertion);
  }

  public static <T> T until(Supplier<T> supplier, Matcher<T> matcher) {
    return until(supplier, matcher, AwaitilitySettings.defaults());
  }

  public static <T> T until(Supplier<T> supplier, Matcher<T> matcher, AwaitilitySettings settings) {
    return awaits(settings).until(get(supplier), matcher);
  }

  public static <T> T until(Supplier<T> supplier, Predicate<T> predicate) {
    return until(supplier, predicate, AwaitilitySettings.defaults());
  }

  public static <T> T until(
      Supplier<T> supplier, Predicate<T> predicate, AwaitilitySettings settings) {
    return awaits(settings).until(get(supplier), predicate);
  }

  private static <T> Callable<T> get(Supplier<T> supplier) {
    return () -> {
      T instance = supplier.get();
      Log.debug("Checking ... {}", instance);
      return instance;
    };
  }

  private static ConditionFactory awaits() {
    return awaits(AwaitilitySettings.defaults());
  }

  private static ConditionFactory awaits(AwaitilitySettings settings) {
    ConditionFactory factory =
        Awaitility.await()
            .pollInterval(settings.interval.toSeconds(), TimeUnit.SECONDS)
            .atMost(timeoutInSeconds(settings), TimeUnit.SECONDS)
            .conditionEvaluationListener(new CustomConditionEvaluationListener(settings));

    if (!settings.doNotIgnoreExceptions) {
      factory = factory.ignoreExceptions();
    }

    return factory;
  }

  private static long timeoutInSeconds(AwaitilitySettings settings) {
    double factor = 1.0;
    if (settings.service != null) {
      factor = settings.service.getConfiguration().getFactorTimeout();
    }

    return Math.round(settings.timeout.toSeconds() * factor);
  }

  public static final class CustomConditionEvaluationListener
      implements ConditionEvaluationListener {

    final AwaitilitySettings settings;

    CustomConditionEvaluationListener(AwaitilitySettings settings) {
      this.settings = settings;
    }

    @Override
    public void conditionEvaluated(EvaluatedCondition condition) {
      if (StringUtils.isNotEmpty(settings.timeoutMessage)) {
        if (settings.service != null) {
          Log.trace(settings.service, condition.getDescription());
        } else {
          Log.debug(condition.getDescription());
        }
      }

      if (!condition.isSatisfied() && settings.onConditionNotMet != null) {
        settings.onConditionNotMet.run();
      }
    }

    @Override
    public void onTimeout(TimeoutEvent timeoutEvent) {
      String message = timeoutEvent.getDescription();
      if (StringUtils.isNotEmpty(message)) {
        message = settings.timeoutMessage;
      }

      if (settings.service != null) {
        Log.warn(settings.service, message);
      } else {
        Log.warn(message);
      }
    }
  }
}
