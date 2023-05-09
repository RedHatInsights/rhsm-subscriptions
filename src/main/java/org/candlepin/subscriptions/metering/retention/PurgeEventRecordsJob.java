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
package org.candlepin.subscriptions.metering.retention;

import org.candlepin.subscriptions.metering.api.admin.InternalMeteringResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurgeEventRecordsJob implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(PurgeEventRecordsJob.class);

  private final InternalMeteringResource meteringResource;

  public PurgeEventRecordsJob(InternalMeteringResource meteringResource) {
    this.meteringResource = meteringResource;
  }

  @Override
  @Scheduled(cron = "${rhsm-subscriptions.jobs.purge-events-schedule}")
  public void run() {
    log.info("Starting PurgeEventRecordsJob job.");

    meteringResource.purgeEventRecords();

    log.info("PurgeEventRecordsJob complete.");
  }
}
