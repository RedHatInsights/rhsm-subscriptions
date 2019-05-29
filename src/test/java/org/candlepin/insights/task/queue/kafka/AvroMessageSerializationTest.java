/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.task.queue.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.insights.task.queue.kafka.message.TaskMessage;

import org.junit.jupiter.api.Test;

import java.util.HashMap;


public class AvroMessageSerializationTest {

    @Test
    public void testMessageCanBeSerializedAndThenDeserialized() {
        AvroSerializer<TaskMessage> serializer = new AvroSerializer<>();
        AvroDeserializer<TaskMessage> deserializer = new AvroDeserializer<>();

        HashMap<String, String> msgArgs = new HashMap<>();
        msgArgs.put("arg1", "arg1-val");

        TaskMessage message = TaskMessage.newBuilder()
            .setType("test-type")
            .setGroupId("test-group")
            .setArgs(msgArgs)
            .build();

        AvroSerializer<TaskMessage> ser = new AvroSerializer<>();
        byte[] messageBytes = ser.serialize("test", message);

        HashMap<String, Object> configs = new HashMap<>();
        configs.put(AvroDeserializer.TARGET_TYPE_CLASS, TaskMessage.class);
        deserializer.configure(configs, false);

        TaskMessage ret = deserializer.deserialize("test", messageBytes);
        assertNotNull(ret);
        assertEquals(message, ret);
    }

}
