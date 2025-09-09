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

import com.redhat.swatch.component.tests.api.Service;
import java.time.Duration;

public final class AwaitilitySettings {

  private static final int POLL_SECONDS = 1;
  private static final int TIMEOUT_SECONDS = 30;

  Duration interval = Duration.ofSeconds(POLL_SECONDS);
  Duration timeout = Duration.ofSeconds(TIMEOUT_SECONDS);
  Service service;
  String timeoutMessage = StringUtils.EMPTY;
  boolean doNotIgnoreExceptions = false;

  public static AwaitilitySettings defaults() {
    return new AwaitilitySettings();
  }

  public static AwaitilitySettings usingTimeout(Duration timeout) {
    AwaitilitySettings settings = defaults();
    settings.timeout = timeout;
    return settings;
  }

  public static AwaitilitySettings using(Duration interval, Duration timeout) {
    AwaitilitySettings settings = defaults();
    settings.interval = interval;
    settings.timeout = timeout;
    return settings;
  }

  public AwaitilitySettings withService(Service service) {
    this.service = service;
    return this;
  }

  public AwaitilitySettings timeoutMessage(String message, Object... args) {
    this.timeoutMessage = String.format(message, args);
    return this;
  }

  public AwaitilitySettings doNotIgnoreExceptions() {
    this.doNotIgnoreExceptions = true;
    return this;
  }
}
