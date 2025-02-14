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
package org.candlepin.subscriptions.kafka;

import com.redhat.swatch.configuration.util.Constants;
import io.opentelemetry.api.internal.StringUtils;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

public class IqeTestMdcProducerInterceptor implements ProducerInterceptor<Object, Object> {
  @Override
  public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
    String iqeTest = MDC.get(Constants.IQE_TEST_HEADER);
    if (!StringUtils.isNullOrEmpty(iqeTest)) {
      record.headers().add(Constants.IQE_TEST_HEADER, iqeTest.getBytes(StandardCharsets.UTF_8));
    }

    return record;
  }

  @Override
  public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> configs) {}
}
