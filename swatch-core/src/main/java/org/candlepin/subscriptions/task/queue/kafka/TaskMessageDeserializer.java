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
package org.candlepin.subscriptions.task.queue.kafka;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.candlepin.subscriptions.task.JsonTaskMessage;
import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Wrapper for the interim step of producing exclusively JSON kafka messages,
 * but supporting any kafka messages that may not have been processed yet that were serialized with Avro.
 */
@Slf4j
public class TaskMessageDeserializer<T> implements Deserializer<T> {

  JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>();
  AvroDeserializer<?> avroDeserializer = new AvroDeserializer<>();

  @Override
  public void configure(Map configs, boolean isKey) {
    jsonDeserializer.addTrustedPackages("org.candlepin.subscriptions.task");

    configs.put(AvroDeserializer.TARGET_TYPE_CLASS, TaskMessage.class);
    avroDeserializer.configure(configs, isKey);
  }


  /*
  * This doesn't actually get called for some reason.  The Deserialize interface is kind of complicated
  * and we're forced to override this because of the interface definition
  */
  @Override
  public T deserialize(String topic, byte[] data) {
    return jsonDeserializer.deserialize(topic, data);
  }

  /*
  * The interface defines a default definition for this, but if we don't override it here then the above
  * deserialize method gets called instead.  Just trust me.
   */
  @Override
  public T deserialize(String topic, Headers headers, byte[] data) {
    Iterable<Header> typeIdHeaders = headers.headers("__TypeId__");

    if (typeIdHeaders.iterator().hasNext()) {
      return jsonDeserializer.deserialize(topic, headers, data);
    } else {
      TaskMessage avroTaskMessage = (TaskMessage) avroDeserializer.deserialize(topic, data);
      return (T) new JsonTaskMessage(avroTaskMessage);
    }
  }
}
