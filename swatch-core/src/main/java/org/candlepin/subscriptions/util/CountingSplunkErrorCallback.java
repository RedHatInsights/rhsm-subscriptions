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
package org.candlepin.subscriptions.util;

import com.splunk.logging.HttpEventCollectorErrorHandler;
import com.splunk.logging.HttpEventCollectorErrorHandler.ErrorCallback;
import com.splunk.logging.HttpEventCollectorEventInfo;
import com.splunk.logging.util.StandardErrorCallback;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A custom Splunk HEC error handler so that we can track failures in a Prometheus metric and then
 * alert if that metric exceeds acceptable parameters.
 *
 * <p>Retrying of failed requests to splunk is handled by HttpEventCollectorResendMiddleware, and
 * the middleware is configured in the logback configuration file.
 */
@Component
public class CountingSplunkErrorCallback implements ErrorCallback {

  private final ErrorCallback delegatedCallback;
  private final Counter failureCounter;

  public CountingSplunkErrorCallback(MeterRegistry meterRegistry) {
    // Only one ErrorCallback can be registered. The standard callback is useful in that it
    // prints the formatted error to stderr, so we'll delegate invocations of our ErrorCallback
    // to the StandardErrorCallback so that it can handle creating a human-readable record of the
    // issue.
    this.delegatedCallback = new StandardErrorCallback(false);
    this.failureCounter = meterRegistry.counter("splunk.hec.message.failure.total");
  }

  @Override
  public void error(List<HttpEventCollectorEventInfo> data, Exception ex) {
    // Splunk HEC batches log messages up. The contents of a failed batch come to us in the
    // HttpEventCollectorEventInfo list. The counter is meant to count the total number of
    // messages that failed to send (not the total number of failed transmissions) so we
    // increment by the size of the list.
    failureCounter.increment(data.size());
    delegatedCallback.error(data, ex);
  }

  @PostConstruct
  public void registerWithSplunk() {
    HttpEventCollectorErrorHandler.onError(this);
  }
}
