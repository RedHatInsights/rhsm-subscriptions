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
package org.candlepin.subscriptions.jmx;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

/**
 * Properties related to JMX beans. The intent is for each JMX bean to have its own static inner
 * class containing its relevant configuration.
 */
@Getter
@Component
@ConfigurationProperties(prefix = "rhsm-subscriptions.jmx")
public class JmxProperties {
  private final TallyBean tallyBean = new TallyBean();

  /** Inner class for properties specifically associated with {@link TallyJmxBean} */
  @Getter
  @Setter
  public static class TallyBean {
    /**
     * Since the two parameters sent to {@link TallyJmxBean#tallyOrgByHourly(String, String,
     * String)} are actually ISO 8601 timestamps we are using a Duration rather than a Period since
     * Duration captures time and not just dates. However, the default ChronoUnit we're using is
     * days since that's what the range is meant to be on the order of. If the value is specified in
     * hours (which Spring will allow: e.g. 2160h) the behavior will be strict: e.g. Daylight Saving
     * Time will not affect it.
     *
     * <p>From the docs: "Durations and periods differ in their treatment of daylight savings time
     * when added to ZonedDateTime. A Duration will add an exact number of seconds, thus a duration
     * of one day is always exactly 24 hours. By contrast, a Period will add a conceptual day,
     * trying to maintain the local time."
     */
    @DurationUnit(ChronoUnit.DAYS)
    private Duration hourlyTallyDurationLimitDays = Duration.ofDays(90);
  }
}
