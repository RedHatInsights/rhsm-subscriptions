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

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * DB repository for Event records.
 *
 * @see EventRecord
 * @see org.candlepin.subscriptions.json.Event
 */
@SuppressWarnings({"linelength", "indentation"})
public interface EventRecordRepository extends JpaRepository<EventRecord, UUID> {

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
   * Check if any Events exist for the specified org and service type during the specified range.
   *
   * @param orgId
   * @param serviceType
   * @param begin
   * @param end
   * @return true if at least 1 event exists, false otherwise.
   */
  @Query(
      nativeQuery = true,
      value =
          "select exists(select 1 from events where org_id=:orgId and data->>'service_type'=:serviceType and timestamp >= :begin and timestamp < :end order by timestamp)")
  boolean existsByOrgIdAndServiceTypeAndTimestampGreaterThanEqualAndTimestampLessThan(
      @Param("orgId") String orgId,
      @Param("serviceType") String serviceType,
      @Param("begin") OffsetDateTime begin,
      @Param("end") OffsetDateTime end);

  /**
   * Find all the events based on the account number and service type that exist during the
   * specified range.
   *
   * @param orgId
   * @param serviceType
   * @param begin
   * @param end
   * @return a stream of Event objects matching the specified criteria.
   */
  @Query(
      nativeQuery = true,
      value =
          "select * from events where org_id=:orgId and data->>'service_type'=:serviceType and timestamp >= :begin and timestamp < :end order by timestamp")
  Stream<EventRecord>
      findByOrgIdAndServiceTypeAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestamp(
          @Param("orgId") String orgId,
          @Param("serviceType") String serviceType,
          @Param("begin") OffsetDateTime begin,
          @Param("end") OffsetDateTime end);

  void deleteByOrgId(String orgId);
}
