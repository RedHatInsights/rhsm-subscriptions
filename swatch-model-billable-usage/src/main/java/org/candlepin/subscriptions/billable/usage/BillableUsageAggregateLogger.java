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
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

/**
 * Appends the aggregate to an SLF4J-style message and logs complete UUID/date collections in
 * conservative, fixed-size chunks.
 */
public final class BillableUsageAggregateLogger {
  // Verified against the production JSON formatter, leaving headroom for message/context/metadata.
  private static final int ENTRIES_PER_CHUNK = 500;

  private BillableUsageAggregateLogger() {}

  public static void debug(
      Logger logger, BillableUsageAggregate aggregate, String message, Object... arguments) {
    log(logger, Level.DEBUG, aggregate, message, arguments);
  }

  public static void info(
      Logger logger, BillableUsageAggregate aggregate, String message, Object... arguments) {
    log(logger, Level.INFO, aggregate, message, arguments);
  }

  public static void warn(
      Logger logger, BillableUsageAggregate aggregate, String message, Object... arguments) {
    log(logger, Level.WARN, aggregate, message, arguments);
  }

  public static void error(
      Logger logger, BillableUsageAggregate aggregate, String message, Object... arguments) {
    log(logger, Level.ERROR, aggregate, message, arguments);
  }

  private static void log(
      Logger logger,
      Level level,
      BillableUsageAggregate aggregate,
      String pattern,
      Object[] arguments) {
    if (!logger.isEnabledForLevel(level)) {
      return;
    }

    List<String> uuids = aggregate == null ? null : aggregate.getRemittanceUuids();
    var snapshotDates = aggregate == null ? null : aggregate.getSnapshotDates();
    boolean needsChunking =
        (uuids != null && uuids.size() > ENTRIES_PER_CHUNK)
            || (snapshotDates != null && snapshotDates.size() > ENTRIES_PER_CHUNK);

    String primaryAggregate =
        aggregate == null
            ? null
            : formatAggregate(
                aggregate,
                needsChunking && snapshotDates != null
                    ? snapshotDates.stream().limit(ENTRIES_PER_CHUNK).toList()
                    : snapshotDates,
                needsChunking && uuids != null ? slice(uuids, 0, ENTRIES_PER_CHUNK) : uuids);
    var formatted = MessageFormatter.arrayFormat(pattern, arguments);
    logger
        .atLevel(level)
        .setCause(formatted.getThrowable())
        .log(formatted.getMessage() + ", aggregate=" + primaryAggregate);

    if (!needsChunking) {
      return;
    }

    if (uuids == null) {
      uuids = List.of();
    }
    List<?> dates = snapshotDates == null ? List.of() : new ArrayList<>(snapshotDates);
    int size = Math.max(uuids.size(), dates.size());
    int chunks = (size + ENTRIES_PER_CHUNK - 1) / ENTRIES_PER_CHUNK;
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

  // Preserve the original Lombok field order and format, including both collections.
  private static String formatAggregate(
      BillableUsageAggregate aggregate, Collection<?> snapshotDates, List<?> remittanceUuids) {
    return "BillableUsageAggregate(totalValue="
        + aggregate.getTotalValue()
        + ", windowTimestamp="
        + aggregate.getWindowTimestamp()
        + ", aggregateId="
        + aggregate.getAggregateId()
        + ", aggregateKey="
        + aggregate.getAggregateKey()
        + ", snapshotDates="
        + snapshotDates
        + ", status="
        + aggregate.getStatus()
        + ", errorCode="
        + aggregate.getErrorCode()
        + ", billedOn="
        + aggregate.getBilledOn()
        + ", remittanceUuids="
        + remittanceUuids
        + ", licenseId="
        + aggregate.getLicenseId()
        + ")";
  }

  private static List<?> slice(List<?> entries, int start, int end) {
    return entries.subList(Math.min(start, entries.size()), Math.min(end, entries.size()));
  }
}
