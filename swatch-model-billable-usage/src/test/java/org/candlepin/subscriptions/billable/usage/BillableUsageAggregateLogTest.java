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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.logging.LoggingFileJsonFormatter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class BillableUsageAggregateLogTest {

  private static final int MAX_LOG_EVENT_BYTES = 60 * 1024;
  private final LoggerCaptor loggerCaptor = new LoggerCaptor();
  private final org.jboss.logmanager.Logger logger =
      LogContext.getLogContext().getLogger(BillableUsageAggregate.class.getName());
  private Level previousLevel;
  private boolean previousUseParentHandlers;

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    previousUseParentHandlers = logger.getUseParentHandlers();
    logger.setLevel(Level.INFO);
    logger.setUseParentHandlers(false);
    logger.addHandler(loggerCaptor);
  }

  @AfterEach
  void restoreLogging() {
    logger.removeHandler(loggerCaptor);
    logger.setLevel(previousLevel);
    logger.setUseParentHandlers(previousUseParentHandlers);
  }

  @Test
  void shouldPreserveOriginalUpdateLogFormatWithoutRepeatingHistoricalUuids() {
    List<String> uuids = createUuids(1001);
    var previousDate = OffsetDateTime.parse("2025-09-30T00:00:00Z");
    var aggregate = new BillableUsageAggregate();
    aggregate.setAggregateId(UUID.randomUUID());
    aggregate.setAggregateKey(new BillableUsageAggregateKey());
    aggregate.setRemittanceUuids(new ArrayList<>(uuids));
    aggregate.getSnapshotDates().add(previousDate);

    var usage =
        new BillableUsage()
            .withOrgId("test-org")
            .withUuid(UUID.randomUUID())
            .withLicenseId("test-license")
            .withSnapshotDate(previousDate.plusDays(1))
            .withValue(1.0);

    aggregate.updateFrom(usage);

    assertEquals(1, loggerCaptor.records.size());
    var record = loggerCaptor.records.getFirst();
    String message = record.getMessage();
    assertEquals(
        "Adding billableUsage: %s to aggregate with aggregateId: %s, totalValue:%s, "
                .formatted(usage, aggregate.getAggregateId(), aggregate.getTotalValue())
            + "licenseId:test-license, remittanceUuids:[%s] and windowTimestamp: %s"
                .formatted(usage.getUuid(), aggregate.getWindowTimestamp()),
        message);
    assertFalse(message.contains("snapshotDates="));
    assertFalse(message.contains(previousDate.toString()));
    uuids.forEach(uuid -> assertFalse(message.contains(uuid)));
    assertTrue(serializedEventBytes(record) < 4 * 1024);
  }

  @Test
  void shouldLogAnEmptyUuidListWhenIncomingUsageHasNoUuid() {
    List<String> uuids = createUuids(1001);
    var aggregate = new BillableUsageAggregate();
    aggregate.setRemittanceUuids(new ArrayList<>(uuids));
    var usage =
        new BillableUsage()
            .withOrgId("test-org")
            .withSnapshotDate(OffsetDateTime.parse("2026-10-01T00:00Z"))
            .withValue(1.0);

    aggregate.updateFrom(usage);

    assertEquals(1, loggerCaptor.records.size());
    var record = loggerCaptor.records.getFirst();
    assertTrue(record.getMessage().contains("remittanceUuids:[] and windowTimestamp: "));
    uuids.forEach(uuid -> assertFalse(record.getMessage().contains(uuid)));
    assertEquals(uuids, aggregate.getRemittanceUuids());
    assertTrue(serializedEventBytes(record) < 4 * 1024);
  }

  @Test
  void shouldLogRepeatedUpdatesOnceAndPreserveCollectionsAtBoundary() {
    List<String> uuids = createUuids(1001);
    var firstDate = OffsetDateTime.parse("2025-10-01T00:00:00Z");
    List<OffsetDateTime> dates =
        IntStream.range(0, uuids.size()).mapToObj(firstDate::plusSeconds).toList();
    var aggregate = new BillableUsageAggregate();

    for (int index = 0; index < uuids.size(); index++) {
      aggregate.updateFrom(
          new BillableUsage()
              .withOrgId("test-org")
              .withUuid(UUID.fromString(uuids.get(index)))
              .withSnapshotDate(dates.get(index))
              .withValue(1.0));
    }

    assertEquals(uuids.size(), loggerCaptor.records.size());
    for (int index = 0; index < uuids.size(); index++) {
      var record = loggerCaptor.records.get(index);
      String message = record.getMessage();
      assertTrue(message.contains("Adding billableUsage:"));
      assertTrue(message.contains(uuids.get(index)));
      assertTrue(message.contains(dates.get(index).toString()));
      assertTrue(message.contains("aggregateId: " + aggregate.getAggregateId()));
      assertTrue(message.contains("remittanceUuids:[" + uuids.get(index) + "]"));
      assertFalse(message.contains("snapshotDates="));
      assertTrue(serializedEventBytes(record) < 4 * 1024);
    }
    assertEquals(uuids, aggregate.getRemittanceUuids());
    assertEquals(new HashSet<>(dates), aggregate.getSnapshotDates());
    assertEquals(uuids.size(), aggregate.getTotalValue().intValueExact());

    loggerCaptor.records.clear();
    BillableUsageAggregateLogger.info(
        LoggerFactory.getLogger(BillableUsageAggregate.class),
        "Sending aggregate to hourly topic: {}",
        aggregate);

    assertChunkedCollectionLogs(uuids, dates, loggerCaptor.records);
  }

  @Test
  void shouldNotCopyTheGrowingUuidListWhenInfoLoggingIsDisabled() {
    var aggregate = new BillableUsageAggregate();
    aggregate.setRemittanceUuids(
        new ArrayList<>(createUuids(1001)) {
          @Override
          public List<String> subList(int fromIndex, int toIndex) {
            throw new AssertionError("Disabled INFO logging must not chunk UUIDs");
          }
        });
    var usage = new BillableUsage().withOrgId("test-org").withValue(1.0);
    logger.setLevel(Level.WARNING);
    aggregate.updateFrom(usage);
    assertTrue(loggerCaptor.records.isEmpty());
  }

  private static void assertChunkedCollectionLogs(
      List<String> uuids, List<OffsetDateTime> dates, List<LogRecord> records) {
    int expectedChunkCount = (Math.max(uuids.size(), dates.size()) + 499) / 500;
    assertEquals(expectedChunkCount, records.size());

    for (int index = 0; index < records.size(); index++) {
      var record = records.get(index);
      String message = record.getMessage();
      if (index == 0) {
        assertTrue(
            message.startsWith("Sending aggregate to hourly topic: BillableUsageAggregate("));
        assertFalse(message.contains("(chunk "));
      } else {
        assertTrue(message.startsWith("Billable usage aggregate continuation:"));
        assertFalse(message.contains("Sending aggregate to hourly topic:"));
        assertTrue(message.contains("(chunk " + (index + 1) + "/" + expectedChunkCount + ")"));
      }
      assertTrue(serializedEventBytes(record) <= MAX_LOG_EVENT_BYTES);
    }

    List<String> messages = records.stream().map(LogRecord::getMessage).toList();
    for (String uuid : uuids) {
      assertEquals(1, messages.stream().filter(message -> message.contains(uuid)).count());
    }
    for (OffsetDateTime date : dates) {
      assertEquals(
          1, messages.stream().filter(message -> message.contains(date.toString())).count());
    }
  }

  private static int serializedEventBytes(LogRecord record) {
    return new LoggingFileJsonFormatter()
        .format(ExtLogRecord.wrap(record))
        .getBytes(StandardCharsets.UTF_8)
        .length;
  }

  private static List<String> createUuids(int count) {
    return IntStream.range(0, count).mapToObj(index -> UUID.randomUUID().toString()).toList();
  }

  private static class LoggerCaptor extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
      records.clear();
    }
  }
}
