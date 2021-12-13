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
package org.candlepin.subscriptions.logback;

import com.splunk.logging.HttpEventCollectorErrorHandler;

/**
 * This is an example/placeholder class to illustrate customized error handling. Retrying of failed
 * requests to splunk is handled by HttpEventCollectorResendMiddleware, and the middleware is
 * configured in the logback configuration file.
 */
public class RhsmSplunkHecUtil {
  private RhsmSplunkHecUtil() {
    // No instances allowed
  }

  /**
   * Soley printing the stacktrace isn't the ideal behavior for production environments. Leaving
   * this here though because the avenue we're going to pursue for error handling is going to
   * probably be configuring an alert in Splunk if X amount of time passes where it doesn't receive
   * any HEC log messages. When that alert triggers, we can then check the container and reference
   * this stack trace to help troubleshoot.
   */
  public static void reportHttpEventCollectorError() {

    HttpEventCollectorErrorHandler.onError((events, e) -> e.printStackTrace());
  }
}
