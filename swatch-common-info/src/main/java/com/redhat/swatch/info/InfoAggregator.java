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
package com.redhat.swatch.info;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the {@code /info} JSON payload from all CDI {@link InfoContributor} beans. */
@ApplicationScoped
public class InfoAggregator {

  private final Instance<InfoContributor> contributors;

  @Inject
  public InfoAggregator(Instance<InfoContributor> contributors) {
    this.contributors = contributors;
  }

  /**
   * Aggregates contributor sections. Contributors that return {@code null} or an empty map /
   * collection are skipped so optional sections (such as feature flags) are absent when unused.
   */
  public Map<String, Object> buildInfo() {
    Map<String, Object> info = new LinkedHashMap<>();
    for (InfoContributor contributor : contributors) {
      Object data = contributor.data();
      if (hasContent(data)) {
        info.put(contributor.name(), data);
      }
    }
    return info;
  }

  private static boolean hasContent(Object data) {
    if (data == null) {
      return false;
    }
    if (data instanceof Map<?, ?> map) {
      return !map.isEmpty();
    }
    if (data instanceof Collection<?> collection) {
      return !collection.isEmpty();
    }
    return true;
  }
}
