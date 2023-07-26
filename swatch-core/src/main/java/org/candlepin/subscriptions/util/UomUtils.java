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
package org.candlepin.subscriptions.util;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.candlepin.subscriptions.json.Measurement.Uom;

public class UomUtils {
  private UomUtils() {
    /* intentionally empty */
  }

  public static Stream<Uom> getUomsFromConfigForTag(String tag) {
    return getUomsFromConfigForVariant(Variant.findByTag(tag).orElse(null));
  }

  public static Stream<Uom> getUomsFromConfigForVariant(Variant variant) {
    return Optional.ofNullable(variant).map(Variant::getSubscription).stream()
        .map(SubscriptionDefinition::getMetrics)
        .flatMap(Collection::stream)
        .map(Metric::getId)
        .map(
            metricId -> {
              /*
              NOTE: Measurement.Uom values use a convention of capitalized kebab-case (e.g.
              Instance-hours). The DB values and our config both use a convention with uppercase
              snake-case (e.g. INSTANCE_HOURS). So in order to match them, we need to make them the
              same case and remove hyphens and underscores (e.g. instancehours).
              */
              var normalized = metricId.replaceAll("[_-]", "").toLowerCase();
              return Arrays.stream(Uom.values())
                  .filter(
                      v ->
                          Objects.equals(
                              v.toString().replaceAll("[_-]", "").toLowerCase(), normalized))
                  .findFirst()
                  .orElseThrow();
            });
  }
}
