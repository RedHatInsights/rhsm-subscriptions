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
package com.redhat.swatch.kafka;

import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class MessageHelper {

  private MessageHelper() {}

  public static Optional<String> findFirstHeaderAsString(
      KafkaMessageMetadata<?> metadata, String headerName) {
    return findFirstHeader(metadata, headerName).map(v -> new String(v, StandardCharsets.UTF_8));
  }

  public static Optional<byte[]> findFirstHeader(
      KafkaMessageMetadata<?> metadata, String headerName) {
    if (metadata == null || metadata.getHeaders() == null) {
      return Optional.empty();
    }
    var values = metadata.getHeaders().headers(headerName);
    if (values != null && values.iterator().hasNext()) {
      var value = values.iterator().next().value();
      if (value != null) {
        return Optional.of(value);
      }
    }

    return Optional.empty();
  }
}
