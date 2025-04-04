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
package com.redhat.swatch.hbi.config;

import io.getunleash.Unleash;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class FeatureFlags {

  public static final String EMIT_EVENTS = "swatch.swatch-metrics-hbi.emit-events";

  private final Unleash unleash;

  public FeatureFlags(Unleash unleash) {
    this.unleash = unleash;
  }

  @PostConstruct
  void logFlagState() {
    log.info("FeatureFlag '{}': {}", EMIT_EVENTS, emitEvents());
  }

  public boolean emitEvents() {
    return isEnabled(EMIT_EVENTS);
  }

  public boolean isEnabled(String flagName) {
    return unleash.isEnabled(flagName);
  }
}
