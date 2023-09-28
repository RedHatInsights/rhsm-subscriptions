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
package org.candlepin.subscriptions.tally;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.util.DateRange;

@Slf4j
public class AccountUsageCalculationCache {

  private OffsetDateTime lastEventApplied;
  private final Map<OffsetDateTime, AccountUsageCalculation> calculations;

  public AccountUsageCalculationCache() {
    this.calculations = new HashMap<>();
  }

  public AccountUsageCalculation get(Event event) {
    return calculations.get(event.getTimestamp());
  }

  public boolean contains(Event event) {
    return calculations.containsKey(event.getTimestamp());
  }

  public void put(Event event, AccountUsageCalculation calc) {
    calculations.put(event.getTimestamp(), calc);
    lastEventApplied = event.getRecordDate();
  }

  public boolean isEventApplied(Event event) {
    return Objects.nonNull(lastEventApplied) && !lastEventApplied.isBefore(event.getRecordDate());
  }

  public Map<OffsetDateTime, AccountUsageCalculation> getCalculations() {
    return calculations;
  }

  public boolean isEmpty() {
    return calculations.isEmpty();
  }

  public DateRange getCalculationRange() {
    OffsetDateTime effectiveStart =
        calculations.keySet().stream().min(OffsetDateTime::compareTo).orElseThrow();

    OffsetDateTime effectiveEnd =
        calculations.keySet().stream().max(OffsetDateTime::compareTo).orElseThrow();

    // During a tally, the end date is not included in the overall existing tally
    // query, therefor we need the range to end 1h after the last calculation.
    return new DateRange(effectiveStart, effectiveEnd.plusHours(1));
  }
}
