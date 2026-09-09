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
package com.redhat.swatch.common.security;

import com.redhat.swatch.kessel.KesselMetricsRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer implementation of {@link KesselMetricsRecorder}.
 *
 * <p>Records Kessel client metrics to Prometheus via Quarkus Micrometer registry. Follows
 * notifications-backend conventions for metric naming and tags.
 */
public class KesselMicrometerRecorder implements KesselMetricsRecorder {

  private static final String METRIC_CHECK_COUNT = "kessel.grpc.check.count";
  private static final String METRIC_CONNECTION_ERRORS = "kessel.grpc.connection.errors";
  private static final String METRIC_CHANNEL_INIT = "kessel.channel.init";

  private static final String TAG_RESULT = "result";
  private static final String RESULT_SUCCESS = "success";
  private static final String RESULT_FAILURE = "failure";

  private final MeterRegistry registry;

  public KesselMicrometerRecorder(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void recordCheckRequest(boolean success) {
    Counter.builder(METRIC_CHECK_COUNT)
        .description("Total Kessel gRPC permission check requests")
        .tags(Tags.of(TAG_RESULT, success ? RESULT_SUCCESS : RESULT_FAILURE))
        .register(registry)
        .increment();
  }

  @Override
  public void recordConnectionError(String errorCode) {
    Counter.builder(METRIC_CONNECTION_ERRORS)
        .description("Kessel gRPC connection errors")
        .tags(Tags.of("error_code", errorCode))
        .register(registry)
        .increment();
  }

  @Override
  public void recordChannelInit(String reason) {
    Counter.builder(METRIC_CHANNEL_INIT)
        .description("Kessel gRPC channel initialization events")
        .tags(Tags.of("reason", reason))
        .register(registry)
        .increment();
  }
}
