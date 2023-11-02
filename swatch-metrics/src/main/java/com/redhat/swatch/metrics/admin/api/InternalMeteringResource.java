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
package com.redhat.swatch.metrics.admin.api;

import com.redhat.swatch.metrics.configuration.MetricProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;

@ApplicationScoped
@Slf4j
public class InternalMeteringResource implements DefaultApi {
  @Inject MetricProperties metricProperties;
  @Inject ApplicationClock clock;

  @Override
  public void syncMetricsForAllAccounts() {
    // To be done in https://issues.redhat.com/browse/SWATCH-1805
  }

  @Override
  public void meterProductForOrgIdAndRange(
      String productTag,
      @NotNull String orgId,
      OffsetDateTime endDate,
      @Min(0) Integer rangeInMinutes,
      Boolean xRhSwatchSynchronousRequest) {
    // To be done in https://issues.redhat.com/browse/SWATCH-1805
  }
}
