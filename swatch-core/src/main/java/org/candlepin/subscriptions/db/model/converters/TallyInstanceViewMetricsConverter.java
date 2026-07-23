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

import com.redhat.swatch.configuration.registry.MetricId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.JacksonException;

/**
 * JPA AttributeConverter for TallyInstanceView metrics. Note: This is NOT a Spring @Component -
 * Hibernate instantiates it directly. Uses Jackson3ObjectMapperHolder to access the Jackson 3
 * ObjectMapper.
 */
@Converter(autoApply = false)
public class TallyInstanceViewMetricsConverter
    implements AttributeConverter<Map<MetricId, Double>, String> {

  private static final String METRIC_ID = "metric_id";
  private static final String VALUE = "value";

  // No-arg constructor required for Hibernate instantiation
  public TallyInstanceViewMetricsConverter() {}

  @Override
  public String convertToDatabaseColumn(Map<MetricId, Double> map) {
    throw new UnsupportedOperationException(
        "This converter should only be used in the TallyInstanceView view which is only read-only mode.");
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<MetricId, Double> convertToEntityAttribute(String s) {
    if (s == null || s.isEmpty()) {
      return Map.of();
    }

    try {
      Map<MetricId, Double> metrics = new HashMap<>();
      Jackson3ObjectMapperHolder.getInstance()
          .readValue(s, List.class)
          .forEach(
              e -> {
                Map<String, Object> m = (Map<String, Object>) e;
                String metricId = (String) m.get(METRIC_ID);
                double value = Optional.ofNullable(m.get(VALUE)).map(this::toDouble).orElse(0.0);
                if (metricId != null) {
                  metrics.put(MetricId.fromString(metricId), value);
                }
              });
      return metrics;
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Error parsing metrics", e);
    }
  }

  private double toDouble(Object o) {
    if (o instanceof Integer i) {
      return i;
    } else if (o instanceof Double d) {
      return d;
    } else if (o instanceof Long l) {
      return l;
    }

    return 0.0;
  }
}
