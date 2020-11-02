/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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

import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;



/**
 * A Kafka message serializer for serializing Avro generated message objects that are sent
 * to Kafka.
 *
 * Based on example found at:
 *     https://codenotfound.com/spring-kafka-apache-avro-serializer-deserializer-example.html
 *
 * @param <T> the object type to serialize.
 */
public class AvroSerializer<T extends SpecificRecordBase> implements Serializer<T> {

    private static final Logger log = LoggerFactory.getLogger(AvroSerializer.class);

    @Override
    public void close() {
        // No-op
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // Nothing to configure.
    }

    @Override
    public byte[] serialize(String topic, T data) {
        try {
            byte[] result = null;

            if (data != null) {
                log.debug("data='{}'", data);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                BinaryEncoder binaryEncoder =
                    EncoderFactory.get().binaryEncoder(byteArrayOutputStream, null);

                DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(data.getSchema());
                datumWriter.write(data, binaryEncoder);

                binaryEncoder.flush();
                byteArrayOutputStream.close();

                result = byteArrayOutputStream.toByteArray();

                if (log.isDebugEnabled()) {
                    log.debug("serialized data='{}'", DatatypeConverter.printHexBinary(result));
                }
            }
            return result;
        }
        catch (Exception ex) {
            throw new SerializationException(
                "Can't serialize data='" + data + "' for topic='" + topic + "'", ex);
        }
    }
}

