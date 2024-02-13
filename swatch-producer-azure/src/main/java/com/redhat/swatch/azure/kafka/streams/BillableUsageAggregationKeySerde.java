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
package com.redhat.swatch.azure.kafka.streams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class BillableUsageAggregationKeySerde implements Serde<BillableUsageAggregateKey> {

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    Serde.super.configure(configs, isKey);
  }

  @Override
  public void close() {
    Serde.super.close();
  }

  @Override
  public Serializer<BillableUsageAggregateKey> serializer() {
    return (topic, data) -> {
      ObjectMapper mapper = new ObjectMapper();
      try {
        return mapper.writeValueAsBytes(data);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Cannot serialize data");
      }
    };
  }

  @Override
  public Deserializer<BillableUsageAggregateKey> deserializer() {
    return (topic, data) -> {
      ObjectMapper mapper = new ObjectMapper();
      try {
        return mapper.readValue(data, BillableUsageAggregateKey.class);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot deserialize data");
      }
    };
  }
}
