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
package org.candlepin.subscriptions.db.model.converters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.configuration.registry.MetricId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class TallyInstanceViewMetricsConverterTest {
  private final TallyInstanceViewMetricsConverter converter =
      new TallyInstanceViewMetricsConverter(JsonMapper.builder().build());

  @Test
  void readsIntegerDecimalAndNullMetrics() {
    assertEquals(
        Map.of(MetricId.fromString("Cores"), 2.0, MetricId.fromString("Sockets"), 1.5),
        converter.convertToEntityAttribute(
            """
            [{"metric_id":"Cores","value":2},{"metric_id":"Sockets","value":1.5},
             {"metric_id":null,"value":3}]
            """));
    assertEquals(
        Map.of(MetricId.fromString("Cores"), 0.0),
        converter.convertToEntityAttribute("[{\"metric_id\":\"Cores\",\"value\":null}]"));
  }

  @Test
  void readsLargeIntegerWithoutLosingItsValue() {
    assertEquals(
        Map.of(MetricId.fromString("Cores"), 3000000000.0),
        converter.convertToEntityAttribute("[{\"metric_id\":\"Cores\",\"value\":3000000000}]"));
  }

  @Test
  void handlesEmptyColumnsAndRejectsMalformedJson() {
    assertEquals(Map.of(), converter.convertToEntityAttribute(null));
    assertEquals(Map.of(), converter.convertToEntityAttribute(""));
    var error =
        assertThrows(
            IllegalArgumentException.class, () -> converter.convertToEntityAttribute("[invalid"));
    assertInstanceOf(JacksonException.class, error.getCause());
  }
}
