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
package org.candlepin.subscriptions.liquibase;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.RollbackImpossibleException;

/** Migration class to fix duplicate subscription records caused by SWATCH-2579. */
public class RemoveIncorrectlySegmentedSubscriptions extends LiquibaseCustomTask {
  // language=PostgreSQL
  public static final String FIND_CANDIDATES =
      """
      SELECT s2.subscription_id, s2.quantity, s2.start_date, s2.end_date FROM (
          SELECT subscription_id FROM subscription GROUP BY subscription_id HAVING COUNT(*) > 2
      ) s
      INNER JOIN subscription s2 ON s2.subscription_id = s.subscription_id
      ORDER BY s2.subscription_id, s2.start_date ASC;
      """;

  // Delete all segments except for the initial subscription and first
  // segment after a quantity change
  // language=PostgreSQL
  public static final String DELETE_INCORRECT_SEGMENTS =
      """
      DELETE FROM %s s
      USING (
          SELECT subscription_id, start_date
          FROM (
              SELECT subscription_id,
                  start_date,
                  quantity,
                  ROW_NUMBER() OVER (
                      PARTITION BY subscription_id, quantity ORDER BY subscription_id, start_date DESC
                  ) AS duplicated_row_number
              FROM subscription
              WHERE subscription_id = ?
          ) windowed
          WHERE duplicated_row_number != 1
      ) d
      WHERE s.subscription_id = d.subscription_id
      AND s.start_date = d.start_date;
      """;

  // Set the end date of segment n to the start date of segment n+1
  // language=PostgreSQL
  public static final String UPDATE_SEGMENT =
      """
      UPDATE subscription
      SET end_date = next_end
      FROM (
          SELECT subscription_id, quantity, start_date, LAG(start_date) OVER (ORDER BY start_date DESC) next_end
          FROM subscription
          WHERE subscription_id = ?
      ) d
      WHERE subscription.start_date = d.start_date
          AND d.next_end IS NOT NULL
          AND d.subscription_id = ?;
      """;

  public static final String LOG_PREFIX = "SWATCH-2579: ";

  protected record SubscriptionRecord(
      String subscriptionId, long quantity, Instant startDate, Instant endDate) {}

  @Override
  public void executeTask(Database database) throws DatabaseException, SQLException {
    ResultSet effectedSubscriptions = executeQuery(FIND_CANDIDATES);

    Map<String, List<SubscriptionRecord>> subscriptionMap = new HashMap<>();

    try (effectedSubscriptions) {
      while (effectedSubscriptions.next()) {
        SubscriptionRecord subRecord = map(effectedSubscriptions);
        subscriptionMap
            .computeIfAbsent(subRecord.subscriptionId(), k -> new ArrayList<>())
            .add(subRecord);
      }
    }
    logger.info(LOG_PREFIX + subscriptionMap.size() + " candidate subscriptions");

    List<String> qualifyingSubscriptions =
        subscriptionMap.entrySet().stream()
            .filter(e -> subscriptionQualifies(e.getValue()))
            .map(Map.Entry::getKey)
            .toList();

    logger.info(
        LOG_PREFIX + qualifyingSubscriptions.size() + " subscriptions qualify for migration");

    for (String s : qualifyingSubscriptions) {
      executeUpdate(DELETE_INCORRECT_SEGMENTS.formatted("subscription_measurements"), s);
      executeUpdate(DELETE_INCORRECT_SEGMENTS.formatted("subscription_product_ids"), s);
      int r = executeUpdate(DELETE_INCORRECT_SEGMENTS.formatted("subscription"), s);
      logger.info(LOG_PREFIX + r + " duplicate records removed for subscription ID " + s);

      executeUpdate(UPDATE_SEGMENT, s, s);
    }
  }

  protected boolean subscriptionQualifies(List<SubscriptionRecord> records) {
    // our HAVING count(*) > 2 clause should ensure this is never the case, but we'll check anyway.
    if (records.size() < 3) {
      return false;
    }

    SubscriptionRecord first = records.get(0);
    SubscriptionRecord second = records.get(1);
    if (first.quantity == second.quantity) {
      // No quantity change means while we have duplicate subscription records; they aren't a result
      // of SWATCH-2579
      return false;
    }

    boolean eligible = true;
    // Start the iterator at the point when the quantity changed
    ListIterator<SubscriptionRecord> iterator = records.listIterator(1);
    while (iterator.hasNext()) {
      SubscriptionRecord r1 = iterator.next();
      if (iterator.hasNext()) {
        SubscriptionRecord r2 = records.get(iterator.nextIndex());
        long delta = r2.startDate.getEpochSecond() - r1.startDate.getEpochSecond();
        // if r2's start is more than 28 hours ahead of r1's, then this collection of duplicates
        // isn't a result of daily syncing.  I picked 28 hours to give a bit of a buffer around
        // the vagaries of sync timing
        eligible &= delta < (28 * 60 * 60);
      }
    }

    return eligible;
  }

  protected SubscriptionRecord map(ResultSet resultSet) throws SQLException {
    Instant endDate;
    // Some subscriptions have no end date.  Set to 100 years in the future and hope our grandkids
    // figure it out
    if (resultSet.getTimestamp("end_date") == null) {
      endDate = OffsetDateTime.now().plusYears(100).toInstant();
    } else {
      endDate = resultSet.getTimestamp("end_date").toInstant();
    }
    return new SubscriptionRecord(
        resultSet.getString("subscription_id"),
        resultSet.getLong("quantity"),
        resultSet.getTimestamp("start_date").toInstant(),
        endDate);
  }

  @Override
  public boolean disableAutoCommit() {
    return true;
  }

  @Override
  public String getConfirmationMessage() {
    return "Clean-up of extraneous subscription segments complete";
  }

  @Override
  public void rollback(Database database)
      throws CustomChangeException, RollbackImpossibleException {
    throw new RollbackImpossibleException("No rollback is possible for this migration");
  }
}
