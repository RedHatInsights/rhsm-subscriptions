/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.task.queue.kafka.message.TaskMessage;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



/**
 * Tests the AvroSerializer failure logic. Successful serialization tests
 * are located in {@link AvroMessageSerializationTest} since both the serialization
 * and deserialization will be tested together.
 */
public class AvroSerializerTest {

    private AvroSerializer<TaskMessage> serializer;

    @BeforeEach
    public void setupTest() {
        serializer = new AvroSerializer<TaskMessage>();
    }

    @Test
    public void testThrowsSerializationException() {
        TaskMessage mockMessage = mock(TaskMessage.class);
        when(mockMessage.getSchema()).thenThrow(new RuntimeException("forced"));
        assertThrows(SerializationException.class, () -> {
            serializer.serialize("test-topic", mockMessage);
        });
    }
}
