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
package org.candlepin.subscriptions.billable.usage;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

/** Logs complete aggregate UUID/date collections in conservative, fixed-size chunks. */
public final class BillableUsageAggregateLogger {
  // Verified against the production JSON formatter, leaving headroom for message/context/metadata.
  private static final int ENTRIES_PER_CHUNK = 500;

  private BillableUsageAggregateLogger() {}

  public static void info(Logger logger, String message, Object... arguments) {
    log(logger, Level.INFO, message, arguments);
  }

  public static void warn(Logger logger, String message, Object... arguments) {
    log(logger, Level.WARN, message, arguments);
  }

  public static void error(Logger logger, String message, Object... arguments) {
    log(logger, Level.ERROR, message, arguments);
  }

  private static void log(Logger logger, Level level, String pattern, Object[] arguments) {
    if (!logger.isEnabledForLevel(level)) {
      return;
    }

    BillableUsageAggregate aggregate = null;
    Object[] primaryArguments = arguments.clone();
    for (int index = 0; index < arguments.length; index++) {
      if (arguments[index] instanceof BillableUsageAggregate value) {
        aggregate = value;
        primaryArguments[index] = primaryAggregate(value);
      }
    }
    var formatted = MessageFormatter.arrayFormat(pattern, primaryArguments);
    logger.atLevel(level).setCause(formatted.getThrowable()).log(formatted.getMessage());

    List<String> uuids = aggregate == null ? null : aggregate.getRemittanceUuids();
    if (uuids == null) {
      uuids = List.of();
    }
    List<?> dates =
        aggregate == null || aggregate.getSnapshotDates() == null
            ? List.of()
            : new ArrayList<>(aggregate.getSnapshotDates());
    int size = Math.max(uuids.size(), dates.size());
    int chunks = Math.max(1, (size + ENTRIES_PER_CHUNK - 1) / ENTRIES_PER_CHUNK);
    for (int index = 1; index < chunks; index++) {
      int start = index * ENTRIES_PER_CHUNK;
      int end = start + ENTRIES_PER_CHUNK;
      logger
          .atLevel(level)
          .log(
              "Billable usage aggregate continuation: aggregateId={}, remittanceUuids={}, snapshotDates={} (chunk {}/{})",
              aggregate.getAggregateId(),
              slice(uuids, start, end),
              slice(dates, start, end),
              index + 1,
              chunks);
    }
  }

  // Preserve the original Lombok field order and format, bounding only the two collections.
  private static String primaryAggregate(BillableUsageAggregate aggregate) {
    return "BillableUsageAggregate(totalValue="
        + aggregate.getTotalValue()
        + ", windowTimestamp="
        + aggregate.getWindowTimestamp()
        + ", aggregateId="
        + aggregate.getAggregateId()
        + ", aggregateKey="
        + aggregate.getAggregateKey()
        + ", snapshotDates="
        + (aggregate.getSnapshotDates() == null
            ? null
            : aggregate.getSnapshotDates().stream().limit(ENTRIES_PER_CHUNK).toList())
        + ", status="
        + aggregate.getStatus()
        + ", errorCode="
        + aggregate.getErrorCode()
        + ", billedOn="
        + aggregate.getBilledOn()
        + ", remittanceUuids="
        + (aggregate.getRemittanceUuids() == null
            ? null
            : slice(aggregate.getRemittanceUuids(), 0, ENTRIES_PER_CHUNK))
        + ", licenseId="
        + aggregate.getLicenseId()
        + ")";
  }

  private static List<?> slice(List<?> entries, int start, int end) {
    return entries.subList(Math.min(start, entries.size()), Math.min(end, entries.size()));
  }
}
