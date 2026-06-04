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
package com.redhat.swatch.hbi.events;

import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.candlepin.clock.ApplicationClock;

/**
 * A configuration class used for testing purposes that extends the original {@code
 * ApplicationConfiguration}. This implementation provides a deterministic, fixed {@code
 * ApplicationClock} for consistent and reproducible results in testing.
 *
 * <ul>
 *   <li>Overrides the {@link ApplicationConfiguration#applicationClock()} method to produce a fixed
 *       {@code ApplicationClock} instance.
 *   <li>Utilizes a fixed reference time (2025-05-12T12:25:00) in UTC to ensure all generated
 *       timestamps are consistent.
 * </ul>
 *
 * <p>This class is annotated with {@code @Mock} and {@code @ApplicationScoped} to define its scope
 * and indicate its use as a mock implementation during testing.
 */
@Mock
@ApplicationScoped
public class TestingApplicationConfiguration extends ApplicationConfiguration {

  @Produces
  @Override
  public ApplicationClock applicationClock() {
    return fixedClock();
  }

  public static ApplicationClock fixedClock() {
    ZoneId utc = ZoneId.of("UTC");
    LocalDateTime reference = LocalDateTime.of(2025, 5, 12, 12, 25, 0, 0);
    ZonedDateTime timeUtc = reference.atZone(utc);
    return new ApplicationClock(Clock.fixed(Instant.from(timeUtc), utc));
  }
}
