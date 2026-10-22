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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.logging.LoggingFileJsonFormatter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BillableUsageAggregateLoggerTest {
  private static final int MAX_LOG_EVENT_BYTES = 60 * 1024;
  private static final Logger LOG = LoggerFactory.getLogger(BillableUsageAggregateLoggerTest.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<ExtLogRecord> records = new ArrayList<>();
  private final Handler handler =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          records.add(ExtLogRecord.wrap(record));
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };
  private final org.jboss.logmanager.Logger logger =
      LogContext.getLogContext().getLogger(getClass().getName());
  private Level previousLevel;
  private boolean previousUseParentHandlers;
  private Map<String, String> previousMdc;

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    previousUseParentHandlers = logger.getUseParentHandlers();
    previousMdc = MDC.copy();
    logger.setLevel(Level.INFO);
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
  }

  @AfterEach
  void restoreLogging() {
    logger.removeHandler(handler);
    logger.setLevel(previousLevel);
    logger.setUseParentHandlers(previousUseParentHandlers);
    MDC.clear();
    previousMdc.forEach(MDC::put);
  }

  @Test
  void preservesAllUuidsAndDistinctSnapshotDatesInBoundedJsonEvents() throws Exception {
    var aggregate = aggregate(10001, 3000);
    var uuids = aggregate.getRemittanceUuids();
    var dates = aggregate.getSnapshotDates();

    BillableUsageAggregateLogger.info(
        LOG, "Sent usage request to AWS: Quantity=42, aggregate={}", aggregate);

    assertEquals(21, records.size());
    assertCollectionsPreserved(aggregate);
    assertSame(uuids, aggregate.getRemittanceUuids());
    assertSame(dates, aggregate.getSnapshotDates());
    assertFalse(aggregate.toString().contains("remittanceUuids="));
    assertFalse(aggregate.toString().contains("snapshotDates="));
    assertBoundedEvents(aggregate);
  }

  @ParameterizedTest
  @CsvSource({"0,0", "1,0", "500,0", "501,0", "1000,0", "1001,0", "0,1001", "1001,1", "1,1001"})
  void preservesCollectionsAtChunkBoundaries(int uuidCount, int dateCount) throws Exception {
    var aggregate = aggregate(uuidCount, dateCount);

    BillableUsageAggregateLogger.info(LOG, "Sending usage, aggregate={}", aggregate);

    assertEquals(Math.max(1, (Math.max(uuidCount, dateCount) + 499) / 500), records.size());
    assertCollectionsPreserved(aggregate);
    assertBoundedEvents(aggregate);
  }

  @Test
  void preservesMetadataInFixedSizeChunks() throws Exception {
    var aggregate = aggregate(1001, 0);
    String metadata = "x".repeat(4 * 1024);
    MDC.put("requestContext", metadata);

    BillableUsageAggregateLogger.info(LOG, "Sending usage request to AWS, aggregate={}", aggregate);

    assertEquals(3, records.size());
    assertCollectionsPreserved(aggregate);
    assertTrue(
        records.stream().allMatch(record -> metadata.equals(record.getMdc("requestContext"))));
    assertBoundedEvents(aggregate);
  }

  @ParameterizedTest
  @CsvSource({"0", "1", "500", "501"})
  void preservesOriginalPrimaryMessageFormatAndArgumentPlacement(int size) throws Exception {
    var aggregate = aggregate(size, size);
    aggregate.setTotalValue(new BigDecimal("42.5"));
    aggregate.setStatus(BillableUsage.Status.FAILED);
    aggregate.setErrorCode(BillableUsage.ErrorCode.INACTIVE);
    aggregate.setBilledOn(OffsetDateTime.parse("2026-10-01T01:00Z"));
    aggregate.setLicenseId("test-license");
    Object[] arguments = {"UsageRecord(Quantity=42, Status=Success)", aggregate};

    BillableUsageAggregateLogger.info(
        LOG, "Sent usage request to AWS: result={}, aggregate={}", arguments);

    String expected =
        "Sent usage request to AWS: result=UsageRecord(Quantity=42, Status=Success), "
            + "aggregate=BillableUsageAggregate(totalValue=42.5, windowTimestamp=2026-10-01T00:00Z, "
            + "aggregateId="
            + aggregate.getAggregateId()
            + ", aggregateKey="
            + aggregate.getAggregateKey()
            + ", snapshotDates="
            + aggregate.getSnapshotDates().stream().limit(500).toList()
            + ", status="
            + aggregate.getStatus()
            + ", errorCode="
            + aggregate.getErrorCode()
            + ", billedOn=2026-10-01T01:00Z, remittanceUuids="
            + aggregate.getRemittanceUuids().subList(0, Math.min(size, 500))
            + ", licenseId=test-license)";
    assertEquals(expected, records.getFirst().getMessage());
    assertEquals(size > 500 ? 2 : 1, records.size());
    assertSame(aggregate, arguments[1]);
    assertCollectionsPreserved(aggregate);
    assertBoundedEvents(aggregate);
  }

  @Test
  void preservesWarnContinuationsWhenInfoIsDisabled() throws Exception {
    logger.setLevel(Level.WARNING);
    var aggregate = aggregate(1001, 0);

    BillableUsageAggregateLogger.warn(LOG, "Subscription not found for aggregate={}", aggregate);

    assertEquals(3, records.size());
    assertTrue(
        records.stream()
            .allMatch(record -> record.getLevel().equals(org.jboss.logmanager.Level.WARN)));
    assertEquals(
        1L,
        records.stream()
            .filter(record -> record.getMessage().startsWith("Subscription not found"))
            .count());
    assertCollectionsPreserved(aggregate);
    assertBoundedEvents(aggregate);
  }

  @Test
  void preservesEscapedMessageDetailsOnlyInThePrimaryEvent() throws Exception {
    var aggregate = aggregate(1001, 3000);
    String response = "雪😀\"\\\n\t".repeat(1000);

    BillableUsageAggregateLogger.info(
        LOG, "Sent usage request to AWS: result={}, aggregate={}", response, aggregate);

    assertTrue(
        records
            .getFirst()
            .getMessage()
            .startsWith("Sent usage request to AWS: result=" + response));
    records.stream()
        .skip(1)
        .forEach(
            record -> {
              assertFalse(record.getMessage().contains("Sent usage request to AWS"));
              assertFalse(record.getMessage().contains("result="));
              assertFalse(record.getMessage().contains(response));
            });
    assertCollectionsPreserved(aggregate);
    assertBoundedEvents(aggregate);
  }

  @Test
  void leavesHeadroomWithLongestSnapshotDatesAndEscapedDetailsAndMetadata() throws Exception {
    var aggregate = aggregate(1001, 0);
    aggregate.setSnapshotDates(
        new LinkedHashSet<>(
            IntStream.range(0, 1001)
                .mapToObj(index -> OffsetDateTime.MAX.minusNanos(index))
                .toList()));
    String details = "雪😀\"\\\n\t".repeat(500);
    MDC.put("org_id", "test-org");
    MDC.put("trace_id", "f".repeat(32));
    MDC.put("requestContext", "雪\"\\\n".repeat(250));

    BillableUsageAggregateLogger.info(
        LOG, "Sent usage request to AWS: result={}, Quantity=42, aggregate={}", details, aggregate);

    assertEquals(3, records.size());
    assertTrue(
        records
            .getFirst()
            .getMessage()
            .startsWith("Sent usage request to AWS: result=" + details + ", Quantity=42,"));
    records.stream()
        .skip(1)
        .forEach(
            record -> {
              assertFalse(record.getMessage().contains("Sent usage request to AWS"));
              assertFalse(record.getMessage().contains("Quantity="));
              assertFalse(record.getMessage().contains("result="));
            });
    assertCollectionsPreserved(aggregate);
    assertBoundedEvents(aggregate);
  }

  @Test
  void preservesTheExceptionOnlyOnTheFirstChunk() throws Exception {
    var aggregate = aggregate(1001, 3000);
    var cause = new IllegalStateException("AWS metering failed");

    BillableUsageAggregateLogger.error(
        LOG, "Error sending AWS usage, aggregate={}", aggregate, cause);

    assertEquals(6, records.size());
    assertSame(cause, records.getFirst().getThrown());
    records.stream().skip(1).forEach(record -> assertNull(record.getThrown()));
    assertTrue(
        records.stream()
            .allMatch(record -> record.getLevel().equals(org.jboss.logmanager.Level.ERROR)));
    assertTrue(records.getFirst().getMessage().startsWith("Error sending AWS usage"));
    records.stream()
        .skip(1)
        .forEach(record -> assertFalse(record.getMessage().contains("Error sending AWS usage")));
    assertCollectionsPreserved(aggregate);
    assertBoundedEvents(aggregate);
  }

  @Test
  void handlesNullAggregatesAndCollections() throws Exception {
    BillableUsageAggregateLogger.info(LOG, "Received null aggregate={}", (Object) null);
    assertEquals(1, records.size());
    assertTrue(records.getFirst().getMessage().contains("aggregate=null"));

    records.clear();
    var aggregate = aggregate(0, 0);
    aggregate.setRemittanceUuids(null);
    aggregate.setSnapshotDates(null);

    BillableUsageAggregateLogger.warn(LOG, "Received empty aggregate={}", aggregate);

    assertEquals(1, records.size());
    assertTrue(records.getFirst().getMessage().contains("remittanceUuids=null"));
    assertTrue(records.getFirst().getMessage().contains("snapshotDates=null"));
    assertBoundedEvents(aggregate);
  }

  @Test
  void doesNotReadCollectionsOrRenderArgumentsForDisabledLogs() {
    logger.setLevel(Level.OFF);
    var aggregate =
        new BillableUsageAggregate() {
          @Override
          public List<String> getRemittanceUuids() {
            throw new AssertionError("Disabled logging must not read UUIDs");
          }

          @Override
          public Set<OffsetDateTime> getSnapshotDates() {
            throw new AssertionError("Disabled logging must not read snapshot dates");
          }

          @Override
          public String toString() {
            throw new AssertionError("Disabled logging must not render the aggregate");
          }
        };
    var argument =
        new Object() {
          @Override
          public String toString() {
            throw new AssertionError("Disabled logging must not render arguments");
          }
        };

    BillableUsageAggregateLogger.info(LOG, "Info: {}, aggregate={}", argument, aggregate);
    BillableUsageAggregateLogger.warn(LOG, "Warning: {}, aggregate={}", argument, aggregate);
    BillableUsageAggregateLogger.error(LOG, "Error: {}, aggregate={}", argument, aggregate);

    assertTrue(records.isEmpty());
  }

  private void assertCollectionsPreserved(BillableUsageAggregate aggregate) {
    assertEquals(aggregate.getRemittanceUuids(), collectionEntries("remittanceUuids"));
    assertEquals(
        aggregate.getSnapshotDates().stream().map(String::valueOf).toList(),
        collectionEntries("snapshotDates"));
  }

  private List<String> collectionEntries(String field) {
    String marker = field + "=[";
    return records.stream()
        .map(LogRecord::getMessage)
        .filter(message -> message.contains(marker))
        .map(message -> message.substring(message.indexOf(marker) + marker.length()))
        .map(message -> message.substring(0, message.indexOf(']')))
        .filter(entries -> !entries.isEmpty())
        .flatMap(entries -> Arrays.stream(entries.split(", ")))
        .toList();
  }

  private void assertBoundedEvents(BillableUsageAggregate aggregate) throws Exception {
    var formatter = new LoggingFileJsonFormatter();
    for (int index = 0; index < records.size(); index++) {
      ExtLogRecord record = records.get(index);
      String json = formatter.format(record);
      assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= MAX_LOG_EVENT_BYTES);
      assertTrue(mapper.readTree(json).has("message"));
      assertTrue(mapper.readTree(json).get("message").asText().contains(record.getMessage()));
      assertTrue(record.getMessage().contains(aggregate.getAggregateId().toString()));
      if (index == 0) {
        assertFalse(record.getMessage().contains("(chunk "));
        assertTrue(record.getMessage().contains("test-org"));
        assertTrue(record.getMessage().contains("INSTANCE_HOURS"));
        assertTrue(record.getMessage().contains("test-account"));
      } else {
        assertTrue(record.getMessage().startsWith("Billable usage aggregate continuation:"));
        assertTrue(
            record.getMessage().contains("(chunk " + (index + 1) + "/" + records.size() + ")"));
        assertFalse(record.getMessage().contains("test-org"));
        assertFalse(record.getMessage().contains("INSTANCE_HOURS"));
        assertFalse(record.getMessage().contains("test-account"));
      }
    }
  }

  private static BillableUsageAggregate aggregate(int uuidCount, int snapshotCount) {
    var aggregate = new BillableUsageAggregate();
    aggregate.setAggregateId(UUID.randomUUID());
    aggregate.setAggregateKey(
        new BillableUsageAggregateKey(
            "test-org", "rosa", "INSTANCE_HOURS", "Premium", "Production", "aws", "test-account"));
    aggregate.setWindowTimestamp(OffsetDateTime.parse("2026-10-01T00:00Z"));
    aggregate.setRemittanceUuids(
        IntStream.range(0, uuidCount).mapToObj(index -> UUID.randomUUID().toString()).toList());
    var start = OffsetDateTime.parse("2026-05-01T00:00Z");
    aggregate.setSnapshotDates(
        new LinkedHashSet<>(IntStream.range(0, snapshotCount).mapToObj(start::plusHours).toList()));
    return aggregate;
  }
}
