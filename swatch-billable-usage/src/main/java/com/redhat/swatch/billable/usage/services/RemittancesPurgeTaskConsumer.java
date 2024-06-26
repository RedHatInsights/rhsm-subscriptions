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
package com.redhat.swatch.billable.usage.services;

import static com.redhat.swatch.billable.usage.configuration.Channels.REMITTANCES_PURGE_TASK;

import com.redhat.swatch.billable.usage.configuration.ApplicationConfiguration;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.model.EnabledOrgsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class RemittancesPurgeTaskConsumer {
  private final ApplicationClock applicationClock;
  private final ApplicationConfiguration configuration;
  private final BillableUsageRemittanceRepository remittanceRepository;

  @Transactional
  @Incoming(REMITTANCES_PURGE_TASK)
  public void consume(EnabledOrgsResponse message) {
    log.debug("Received task for remittances purge with org ID: {}", message.getOrgId());
    OffsetDateTime cutoffDate = getCutoffDate();
    if (cutoffDate == null) {
      log.warn(
          "Skipping purge remittances task for org ID '{}' because the policy duration is not configured. ",
          message.getOrgId());
      return;
    }
    log.info(
        "Delete usage remittances for org ID '{}' with cut off date of '{}'",
        message.getOrgId(),
        cutoffDate);
    remittanceRepository.deleteAllByOrgIdAndRemittancePendingDateBefore(
        message.getOrgId(), cutoffDate);
  }

  /**
   * Get the cutoff date for BillableUsageRemittanceEntity records to be kept.
   *
   * <p>Any remittance of this granularity older than the cutoff date should be removed.
   *
   * @return cutoff date (i.e. dates less than this are candidates for removal), or null
   */
  private OffsetDateTime getCutoffDate() {
    var policyDuration = configuration.getRemittanceRetentionPolicyDuration();
    if (policyDuration != null) {
      return applicationClock.now().minus(policyDuration);
    }

    return null;
  }
}
