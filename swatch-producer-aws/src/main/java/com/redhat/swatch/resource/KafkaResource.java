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
package com.redhat.swatch.resource;

import com.redhat.swatch.kafka.KafkaSeekHelper;
import com.redhat.swatch.openapi.model.KafkaSeekPosition;
import com.redhat.swatch.openapi.resource.KafkaApi;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;

public class KafkaResource implements KafkaApi {
  private final KafkaSeekHelper kafkaSeekHelper;

  @Inject
  KafkaResource(KafkaSeekHelper kafkaSeekHelper) {
    this.kafkaSeekHelper = kafkaSeekHelper;
  }

  @Override
  public void kakfaSeekPosition(KafkaSeekPosition position) throws ProcessingException {
    kafkaSeekHelper.seekToPosition(position);
  }

  @Override
  public void kakfaSeekTimestamp(String timestamp) throws ProcessingException {
    kafkaSeekHelper.seekToTimestamp(OffsetDateTime.parse(timestamp));
  }
}
