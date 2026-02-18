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
package com.redhat.swatch.utilization.service;

import static com.redhat.swatch.utilization.configuration.Channels.UTILIZATION;

import com.redhat.swatch.utilization.model.UtilizationSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class UtilizationSummaryConsumer {

  @Inject UtilizationSummaryPayloadValidator payloadValidator;
  @Inject UtilizationSummaryMeasurementValidator measurementValidator;
  @Inject CustomerOverUsageService customerOverUsageService;

  @Incoming(UTILIZATION)
  public void process(UtilizationSummary payload) {
    if (payload.getMeasurements() == null || payload.getMeasurements().isEmpty()) {
      log.debug("Received utilization summary without measurements. Payload: {}", payload);
      return;
    }

    if (!payloadValidator.isUtilizationSummaryValid(payload)) {
      log.debug("Invalid payload received: {}", payload);
      return;
    }

    for (var measurement : payload.getMeasurements()) {
      if (measurementValidator.isMeasurementValid(payload, measurement)) {
        customerOverUsageService.check(payload, measurement);
      }
    }
  }
}
