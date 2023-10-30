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
package org.candlepin.subscriptions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.candlepin.clock.ApplicationClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FixedClockConfiguration {
  // A zoneless time
  public static final LocalDateTime SPRING_TIME = LocalDateTime.of(2019, 5, 24, 12, 35, 0, 0);
  public static final ZonedDateTime SPRING_TIME_UTC = SPRING_TIME.atZone(ZoneId.of("UTC"));
  public static final ZonedDateTime SPRING_TIME_EDT =
      SPRING_TIME.atZone(ZoneId.of("America/New_York"));

  public static final LocalDateTime WINTER_TIME = LocalDateTime.of(2019, 1, 3, 14, 15, 0, 0);
  public static final ZonedDateTime WINTER_TIME_UTC = WINTER_TIME.atZone(ZoneId.of("UTC"));
  public static final ZonedDateTime WINTER_TIME_EST =
      WINTER_TIME.atZone(ZoneId.of("America/New_York"));

  @Bean
  @Primary
  public ApplicationClock fixedClock() {
    return new ApplicationClock(Clock.fixed(Instant.from(SPRING_TIME_UTC), ZoneId.of("UTC")));
  }

  @Bean(name = "ZuluWinterClock")
  public ApplicationClock utcWinterClock() {
    return new ApplicationClock(Clock.fixed(Instant.from(WINTER_TIME_UTC), ZoneId.of("UTC")));
  }

  @Bean(name = "EDTSpringClock")
  public ApplicationClock edtSpringClock() {
    return new ApplicationClock(
        Clock.fixed(Instant.from(SPRING_TIME_EDT), ZoneId.of("America/New_York")));
  }

  @Bean(name = "ESTWinterClock")
  public ApplicationClock estWinterClock() {
    return new ApplicationClock(
        Clock.fixed(Instant.from(WINTER_TIME_EST), ZoneId.of("America/New_York")));
  }
}
