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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InfoAggregatorTest {

  @Test
  void shouldMergeNonEmptyContributorSections() {
    InfoContributor os = contributor("os", Map.of("name", "Linux", "arch", "amd64"));
    InfoContributor flags =
        contributor("feature-flags", List.of(Map.of("name", "swatch.example", "enabled", true)));

    Map<String, Object> info = new InfoAggregator(instanceOf(os, flags)).buildInfo();

    assertEquals(2, info.size());
    assertEquals("Linux", ((Map<?, ?>) info.get("os")).get("name"));
    assertTrue(info.containsKey("feature-flags"));
  }

  @Test
  void shouldOmitEmptyAndNullContributorData() {
    InfoContributor emptyList = contributor("feature-flags", List.of());
    InfoContributor emptyMap = contributor("empty-map", Map.of());
    InfoContributor nullData =
        new InfoContributor() {
          @Override
          public String name() {
            return "ignored";
          }

          @Override
          public Object data() {
            return null;
          }
        };
    InfoContributor os = contributor("os", Map.of("name", "Linux"));

    Map<String, Object> info =
        new InfoAggregator(instanceOf(emptyList, emptyMap, nullData, os)).buildInfo();

    assertEquals(1, info.size());
    assertTrue(info.containsKey("os"));
    assertFalse(info.containsKey("feature-flags"));
    assertFalse(info.containsKey("empty-map"));
    assertFalse(info.containsKey("ignored"));
  }

  private static InfoContributor contributor(String name, Object data) {
    return new InfoContributor() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public Object data() {
        return data;
      }
    };
  }

  @SafeVarargs
  private static Instance<InfoContributor> instanceOf(InfoContributor... contributors) {
    @SuppressWarnings("unchecked")
    Instance<InfoContributor> instance = mock(Instance.class);
    when(instance.iterator()).thenAnswer(invocation -> List.of(contributors).iterator());
    return instance;
  }
}
