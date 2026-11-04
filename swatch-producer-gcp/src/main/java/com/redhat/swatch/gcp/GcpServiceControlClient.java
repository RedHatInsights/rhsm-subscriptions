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
package com.redhat.swatch.gcp;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.api.servicecontrol.v1.ServiceControllerClient;
import com.google.api.servicecontrol.v1.ServiceControllerSettings;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;

@ApplicationScoped
public class GcpServiceControlClient {
  private final GcpConfiguration configuration;
  private ServiceControllerClient client;

  public GcpServiceControlClient(GcpConfiguration configuration) {
    this.configuration = configuration;
  }

  public URI endpoint(String serviceName, String method) {
    if (!serviceName.matches("[a-zA-Z0-9.-]+")
        || !(method.equals("check") || method.equals("report"))) {
      throw new IllegalArgumentException("Invalid Service Control path");
    }
    return baseEndpoint().resolve("/v1/services/" + serviceName + ":" + method);
  }

  public void checkAndReport(CheckRequest check, ReportRequest report) {
    var sdk = sdkClient();
    if (sdk.check(check).getCheckErrorsCount() != 0) {
      throw new IllegalStateException("Service Control returned checkErrors");
    }
    if (sdk.report(report).getReportErrorsCount() != 0) {
      throw new IllegalStateException("Service Control returned reportErrors");
    }
  }

  private URI baseEndpoint() {
    return configuration.emulatorEndpoint().orElse(configuration.endpoint());
  }

  // Lazy creation keeps preview mode independent of ADC and avoids opening SDK resources.
  private synchronized ServiceControllerClient sdkClient() {
    if (client == null) {
      try {
        var settings =
            ServiceControllerSettings.newHttpJsonBuilder().setEndpoint(baseEndpoint().toString());
        if (configuration.emulatorEndpoint().isPresent()) {
          if (!java.util.Set.of("localhost", "127.0.0.1", "::1")
              .contains(baseEndpoint().getHost())) {
            throw new IllegalArgumentException("The unauthenticated emulator must use loopback");
          }
          settings.setCredentialsProvider(NoCredentialsProvider.create());
        }
        // Keep POC submissions to one attempt; production needs a durable retry policy.
        for (var call : java.util.List.of(settings.checkSettings(), settings.reportSettings())) {
          call.setRetryableCodes();
          call.setRetrySettings(
              call.getRetrySettings().toBuilder()
                  .setInitialRpcTimeoutDuration(configuration.requestTimeout())
                  .setMaxRpcTimeoutDuration(configuration.requestTimeout())
                  .setTotalTimeoutDuration(configuration.requestTimeout())
                  .setMaxAttempts(1)
                  .build());
        }
        client = ServiceControllerClient.create(settings.build());
      } catch (IOException e) {
        throw new IllegalStateException("Could not initialize Google Service Control SDK / ADC", e);
      }
    }
    return client;
  }

  @PreDestroy
  synchronized void close() {
    if (client != null) {
      client.close();
    }
  }
}
