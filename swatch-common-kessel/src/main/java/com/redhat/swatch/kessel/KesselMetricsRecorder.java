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
package com.redhat.swatch.kessel;

/**
 * Callback interface for recording Kessel client metrics.
 *
 * <p>Implementations can use Micrometer, Prometheus client, or any other metrics library. The
 * Kessel client remains framework-agnostic by accepting this interface.
 */
public interface KesselMetricsRecorder {

  /**
   * Record a gRPC check request completion.
   *
   * <p>Success indicates Kessel responded (service is healthy), regardless of whether permission
   * was allowed or denied. Failure indicates connection error (Kessel unavailable, timeout, etc.).
   *
   * @param success true if Kessel responded, false if connection failed
   */
  void recordCheckRequest(boolean success);

  /**
   * Record a gRPC connection error.
   *
   * @param errorCode gRPC status code name (e.g., "UNAVAILABLE", "DEADLINE_EXCEEDED")
   */
  void recordConnectionError(String errorCode);

  /**
   * Record a channel initialization event.
   *
   * @param reason "startup", "unauthenticated", or "unhealthy_channel"
   */
  void recordChannelInit(String reason);

  /** No-op implementation for when metrics are disabled. */
  KesselMetricsRecorder NOOP =
      new KesselMetricsRecorder() {
        @Override
        public void recordCheckRequest(boolean success) {}

        @Override
        public void recordConnectionError(String errorCode) {}

        @Override
        public void recordChannelInit(String reason) {}
      };
}
