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
package com.redhat.swatch.contract.service.export;

import static com.redhat.swatch.contract.config.Channels.EXPORT_REQUESTS_TOPIC;

import com.redhat.swatch.export.ExportRequestHandler;
import com.redhat.swatch.export.ExportServiceException;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
@AllArgsConstructor
public class ExportRequestConsumer {

  private final ExportRequestHandler exportService;

  @Timed("rhsm-subscriptions.exports.upload")
  @Transactional
  @Incoming(EXPORT_REQUESTS_TOPIC)
  public void receive(String exportEvent) {
    try {
      exportService.handle(exportEvent);
    } catch (ExportServiceException ex) {
      log.error(
          "Error handling export request: {}. This request will be ignored. "
              + "See the previous errors for further details",
          exportEvent,
          ex);
    }
  }
}
