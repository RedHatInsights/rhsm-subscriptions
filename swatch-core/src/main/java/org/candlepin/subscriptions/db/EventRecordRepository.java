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
package org.candlepin.subscriptions.db;

import static org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE;

import com.google.common.collect.Lists;
import jakarta.persistence.QueryHint;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * DB repository for Event records.
 *
 * @see EventRecord
 * @see org.candlepin.subscriptions.json.Event
 */
@SuppressWarnings({"linelength", "indentation"})
public interface EventRecordRepository
    extends JpaRepository<EventRecord, UUID>, EntityManagerLookup {

  /**
   * Fetch a stream of events for a given account for a given time range.
   *
   * <p>The events returned include those at begin and up to (but not including) end.
   *
   * <p>NOTE: this query does not use `between` since between semantics are inclusive. e.g. `select
   * * from events where timestamp between '2021-01-01T00:00:00Z' and '2021-01-01T01:00:00Z` would
   * match events at midnight UTC and 1am UTC.
   *
   * @param orgId account number
   * @param begin start of the time range (inclusive)
   * @param end end of the time range (exclusive)
   * @return Stream of EventRecords
   */
  Stream<EventRecord> findByOrgIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
      String orgId, OffsetDateTime begin, OffsetDateTime end);

  /**
   * Fetch a stream of events for a given account, event type and event source for a given time
   * range.
   *
   * <p>The events returned include those at begin and up to (but not including) end.
   *
   * <p>NOTE: this query does not use `between` since between semantics are inclusive. e.g. `select
   * * from events where timestamp between '2021-01-01T00:00:00Z' and '2021-01-01T01:00:00Z` would
   * match events at midnight UTC and 1am UTC.
   *
   * @param orgId account number
   * @param eventSource event source
   * @param eventType event type
   * @param begin start of the time range (inclusive)
   * @param end end of the time range (exclusive)
   * @return Stream of EventRecords
   */
  Stream<EventRecord>
      findByOrgIdAndEventSourceAndEventTypeAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
          String orgId,
          String eventSource,
          String eventType,
          OffsetDateTime begin,
          OffsetDateTime end);

  // Wrapper for
  // findByOrgIdAndEventSourceAndEventTypeIsInAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp
  default Stream<EventRecord> findEventRecordsByCriteria(
      String orgId,
      String eventSource,
      String eventType,
      OffsetDateTime begin,
      OffsetDateTime end) {
    return findByOrgIdAndEventSourceAndEventTypeAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
        orgId, eventSource, eventType, begin, end);
  }

  /**
   * Delete old event records given a cutoff date
   *
   * <p>By default, a derived delete query will return instances matching the where clause prior to
   * deleting the entities one at a time. This was too memory intensive. Since we're not concerned
   * with invoking any lifecycle callbacks, we can override the behavior with @query to delete all
   * matching records in bulk. See the <a
   * href="https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.modifying-queries.derived-delete">Spring
   * Data documentation</a> on deletion.
   *
   * @param cutoffDate Dates before this timestamp get deleted
   */
  @Modifying
  @Query("DELETE FROM EventRecord e WHERE e.timestamp<:cutoffDate")
  void deleteInBulkEventRecordsByTimestampBefore(OffsetDateTime cutoffDate);

  /**
   * Delete old event records given a cutoff date and an organization id.
   *
   * @param orgId The organization id
   * @param eventSource The event source
   * @param eventType The event type
   * @param meteringBatchId The metering batch ID to exclude.
   * @param begin Start time window to query events to delete.
   * @param end End time window to query events to delete.
   */
  @Modifying
  @Query(
      "DELETE FROM EventRecord e "
          + "WHERE e.orgId=:orgId "
          + "AND e.eventSource=:eventSource "
          + "AND e.eventType=:eventType "
          + "AND (e.meteringBatchId IS NULL OR e.meteringBatchId != :meteringBatchId)"
          + "AND e.timestamp>=:begin "
          + "AND e.timestamp<:end ")
  int deleteStaleEvents(
      String orgId,
      String eventSource,
      String eventType,
      UUID meteringBatchId,
      OffsetDateTime begin,
      OffsetDateTime end);

  void deleteByOrgId(String orgId);

  void deleteByEventId(UUID eventId);

  @Query(
      nativeQuery = true,
      value =
          """
          select * from events
            where org_id=:orgId and data->>'service_type'=:serviceType and
              record_date > :after
              order by record_date asc
          """)
  @QueryHints(value = {@QueryHint(name = HINT_FETCH_SIZE, value = "1024")})
  Stream<EventRecord> fetchOrderedEventStream(
      @Param("orgId") String orgId,
      @Param("serviceType") String serviceType,
      @Param("after") OffsetDateTime after);

  /**
   * Find all {@link EventRecord}s that share the same lookup key.
   *
   * <pre>
   *   NOTE: This method builds a set of tuples from the supplied keys and executes
   *         a native Postgres query. This is not possible in JPA.
   * </pre>
   *
   * @param keys the {@link EventKey} to match on.
   * @return a list of conflicting events
   */
  default List<EventRecord> findConflictingEvents(List<EventKey> keys) {
    List<EventRecord> found = new ArrayList<>();
    // Partition the incoming keys to ensure we do not exceed the IN clause limit
    // for Postgres.
    for (List<EventKey> nextBatch : Lists.partition(keys, Short.MAX_VALUE)) {
      Set<String> matchingTuples =
          nextBatch.stream()
              .map(
                  e ->
                      String.format(
                          "('%s', '%s', '%s', '%s', '%s')",
                          e.getOrgId(),
                          e.getEventType(),
                          e.getEventSource(),
                          e.getInstanceId(),
                          e.getTimestamp()))
              .collect(Collectors.toSet());

      String query =
          String.format(
              """
              select * from events
              where (org_id, event_type, event_source, instance_id, timestamp)
              in (%s)
              """,
              String.join(",", matchingTuples));
      found.addAll(getEntityManager().createNativeQuery(query, EventRecord.class).getResultList());
    }
    return found;
  }
}
