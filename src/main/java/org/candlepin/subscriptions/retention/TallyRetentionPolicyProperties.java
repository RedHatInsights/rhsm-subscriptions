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
package org.candlepin.subscriptions.retention;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Retention policies for supported granularities. */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "rhsm-subscriptions.tally-retention-policy")
@Validated
public class TallyRetentionPolicyProperties {
  /**
   * Number of historic hourly snapshots to keep. Actual number kept will include an additional hour
   * (current & historic).
   */
  private Integer hourly;

  /**
   * Number of historic daily snapshots to keep. Actual number kept will include an additional day
   * (current & historic).
   */
  private Integer daily;

  /**
   * Number of full weeks of snapshot data to keep. Actual number kept will include an additional
   * week (the current incomplete week).
   */
  private Integer weekly;

  /**
   * Number of full months of snapshot data to keep. Actual number kept will include an additional
   * month (the current incomplete month).
   */
  private Integer monthly;

  /**
   * Number of full quarters of snapshot data to keep. Actual number kept will include an additional
   * quarter (the current incomplete quarter).
   */
  private Integer quarterly;

  /**
   * Number of full years of snapshot data to keep. Actual number kept will include an additional
   * year (the current incomplete year).
   */
  private Integer yearly;

  @Positive private long snapshotsToDeleteInBatches;
}
