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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.insights.task.queue.kafka.message.TaskMessage;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;



/**
 * Tests the AvroDeserializer failure logic. Successful deserialization tests
 * are located in {@link AvroMessageSerializationTest} since both the serialization
 * and deserialization will be tested together.
 */
public class AvroDeserializerTest {

    private AvroDeserializer<TaskMessage> deserializer;

    @BeforeEach
    public void setupTest() {
        deserializer = new AvroDeserializer();
    }

    @Test
    public void testThrowsSerializationExceptionOnError() {
        HashMap<String, Object> configs = new HashMap<>();
        configs.put(AvroDeserializer.TARGET_TYPE_CLASS, TaskMessage.class);
        deserializer.configure(configs, false);

        TaskMessage mockMessage = mock(TaskMessage.class);
        assertThrows(SerializationException.class, () -> {
            // Can not deserialize empty byte array to object.
            deserializer.deserialize("test", new byte[0]);
        });
    }

    @Test
    public void canConfigureTargetClassWithStringOrClassObject() {
        // Will throw an exception if invalid.
        HashMap<String, Object> configs = new HashMap<>();

        // Config by class
        configs.put(AvroDeserializer.TARGET_TYPE_CLASS, TaskMessage.class);
        deserializer.configure(configs, false);
        assertEquals(TaskMessage.class.getCanonicalName(), deserializer.getTargetType().getCanonicalName());

        // Config by String
        configs.put(AvroDeserializer.TARGET_TYPE_CLASS, TaskMessage.class.getCanonicalName());
        deserializer.configure(configs, false);
        assertEquals(TaskMessage.class.getCanonicalName(), deserializer.getTargetType().getCanonicalName());
    }

    @Test
    public void throwsExceptionOnDeserializationWhenNotConfigured() {
        assertThrows(IllegalStateException.class, () -> {
            deserializer.deserialize("topic", new byte[0]);
        });
    }

    @Test
    public void testMissingTargetClassConfiguration() {
        assertThrows(IllegalStateException.class, () -> {
            deserializer.deserialize("test", new byte[0]);
        });
    }

    @Test
    public void testTargetClassNotFound() {
        HashMap<String, Object> configs = new HashMap<>();
        configs.put(AvroDeserializer.TARGET_TYPE_CLASS, "not.found.Message");

        assertThrows(IllegalStateException.class, () -> {
            deserializer.configure(configs, false);
        });
    }

}
