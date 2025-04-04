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
package com.redhat.swatch.hbi.infra;

import io.getunleash.FakeUnleash;
import io.getunleash.Unleash;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

@Alternative
@Priority(1)
@ApplicationScoped
public class FakeUnleashProducer {

  private static final FakeUnleash FAKE_UNLEASH = new FakeUnleash();

  static {
    // default: disable all flags
    FAKE_UNLEASH.resetAll();
  }

  @Produces
  public Unleash unleash() {
    return FAKE_UNLEASH;
  }

  public static FakeUnleash getInstance() {
    return FAKE_UNLEASH;
  }
}
