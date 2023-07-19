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
package org.candlepin.subscriptions.test;

import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * A test execution listener that resets the ApplicationClock to {@link
 * TestClockConfiguration#SPRING_TIME_UTC} before every test setup. We do this to allow tests to
 * modify the ApplicationClock without having to worry about polluting the application context for
 * other tests.
 */
public class ClockResetTestExecutionListener implements TestExecutionListener {
  @Override
  public void beforeTestMethod(TestContext testContext) {
    if (testContext.getApplicationContext().containsBean("adjustableClock")) {
      ApplicationClock applicationClock =
          testContext.getApplicationContext().getBean("adjustableClock", ApplicationClock.class);
      var testClock = (TestClock) applicationClock.getClock();
      testClock.setInstant(TestClockConfiguration.SPRING_TIME_UTC.toInstant());
      testClock.setZone(TestClockConfiguration.SPRING_TIME_UTC.getZone());
    }
  }
}
