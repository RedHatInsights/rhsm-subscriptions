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
package org.candlepin.subscriptions.tally.job;

import org.candlepin.subscriptions.exception.JobFailureException;
import org.candlepin.subscriptions.product.OfferingSyncController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A cron job to sync offerings to the latest upstream state for all non denylisted offerings. */
@Component
@Profile("offering-sync")
public class OfferingSyncJob implements Runnable {

  private final OfferingSyncController controller;

  @Autowired
  public OfferingSyncJob(OfferingSyncController controller) {
    this.controller = controller;
  }

  @Override
  @Scheduled(cron = "${rhsm-subscriptions.jobs.offering-sync-schedule}")
  public void run() {
    try {
      controller.syncAllOfferings();
    } catch (Exception e) {
      throw new JobFailureException("Failed to run " + this.getClass().getSimpleName(), e);
    }
  }
}
