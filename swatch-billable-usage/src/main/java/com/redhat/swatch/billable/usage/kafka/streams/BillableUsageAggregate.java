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
package com.redhat.swatch.billable.usage.kafka.streams;

import com.redhat.swatch.billable.usage.model.BillableUsage;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@RegisterForReflection
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Slf4j
public class BillableUsageAggregate {

  private BigDecimal totalValue = new BigDecimal(0);
  private OffsetDateTime windowTimestamp;
  private UUID aggregateId;
  private BillableUsageAggregateKey aggregateKey;
  private Set<OffsetDateTime> snapshotDates = new HashSet<>();

  public BillableUsageAggregate updateFrom(BillableUsage billableUsage) {
    if (aggregateId == null) {
      aggregateId = UUID.randomUUID();
    }
    if (aggregateKey == null) {
      aggregateKey = new BillableUsageAggregateKey(billableUsage);
    }
    if (windowTimestamp == null) {
      windowTimestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
    }
    totalValue = totalValue.add(BigDecimal.valueOf(billableUsage.getValue()));
    snapshotDates.add(billableUsage.getSnapshotDate());
    log.info(
        "Adding billableUsage: {} to aggregate with aggregateId: {}, totalValue:{} and windowTimestamp: {}",
        billableUsage,
        aggregateId,
        totalValue,
        windowTimestamp);
    return this;
  }
}
