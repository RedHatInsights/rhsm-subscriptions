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
package org.candlepin.subscriptions.task.queue.kafka;


import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

/**
 * A Kafka message deserializer for deserializing Avro generated message objects received
 * from Kafka. The {@value TARGET_TYPE_CLASS} configuration property must be set to the
 * class of the intended object to be serialized.
 *
 * Based on example found at:
 *     https://codenotfound.com/spring-kafka-apache-avro-serializer-deserializer-example.html
 *
 * @param <T> the object type to serialize.
 */
public class AvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {

    private static final Logger log = LoggerFactory.getLogger(AvroDeserializer.class);

    public static final String TARGET_TYPE_CLASS = "rhsm-conduit.avro.deserializer.target.class";

    private Class<T> targetType;

    @Override
    public void close() {
        // No op
    }

    @Override
    public void configure(Map<String, ?> config, boolean isKey) {
        targetType = getTargetType(config);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(String topic, byte[] data) {
        if (this.targetType == null) {
            throw new IllegalStateException("Target type is null.");
        }

        try {
            T result = null;

            if (data != null) {
                if (log.isDebugEnabled()) {
                    log.debug("data='{}'", DatatypeConverter.printHexBinary(data));
                }

                DatumReader<GenericRecord> datumReader =
                    new SpecificDatumReader<>(targetType.newInstance().getSchema());
                Decoder decoder = DecoderFactory.get().binaryDecoder(data, null);

                result = (T) datumReader.read(null, decoder);
                log.debug("deserialized data='{}'", result);
            }
            return result;
        }
        catch (Exception ex) {
            throw new SerializationException(
                "Can't deserialize data '" + Arrays.toString(data) + "' from topic '" + topic + "'", ex);
        }
    }

    public Class<T> getTargetType() {
        return this.targetType;
    }

    private Class getTargetType(Map<String, ?> config) {
        Assert.state(config.containsKey(TARGET_TYPE_CLASS),
            String.format("Target type class not configured for AvroDeserializer: %s", TARGET_TYPE_CLASS));

        try {
            Object value = config.get(TARGET_TYPE_CLASS);
            return value instanceof Class ? (Class<?>) value : ClassUtils.forName((String) value, null);
        }
        catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("Unable to find AvroDeserializer target class", e);
        }
    }
}
