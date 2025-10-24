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
package com.redhat.swatch.component.tests.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.redhat.swatch.component.tests.utils.AssertUtils;
import com.redhat.swatch.component.tests.utils.ClassUtils;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.component.tests.utils.StringUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Generic {@link org.apache.kafka.common.serialization.Serializer Serializer} for sending Java
 * objects to Kafka as JSON.
 *
 * <p>IMPORTANT: Configuration must be done completely with property setters or via {@link
 * #configure(Map, boolean)}, not a mixture. If any setters have been called, {@link #configure(Map,
 * boolean)} will be a no-op.
 *
 * @param <T> class of the entity, representing messages
 */
public class KafkaJsonSerializer<T> implements Serializer<T> {
  public static final String ADD_TYPE_INFO_HEADERS = "swatch.json.add.type.headers";

  /** Kafka config property to add type mappings to the type mapper: 'foo:com.Foo,bar:com.Bar'. */
  public static final String TYPE_MAPPINGS = "swatch.json.type.mapping";

  protected final ObjectMapper objectMapper;

  protected boolean addTypeInfo = true;

  private ObjectWriter writer;

  protected Jackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper(); // NOSONAR

  private boolean typeMapperExplicitlySet = false;

  private boolean setterCalled;

  private boolean configured;

  private final Lock globalLock = new ReentrantLock();

  public KafkaJsonSerializer() {
    this((JavaType) null, JsonUtils.getObjectMapper());
  }

  public KafkaJsonSerializer(TypeReference<? super T> targetType) {
    this(targetType, JsonUtils.getObjectMapper());
  }

  public KafkaJsonSerializer(TypeReference<? super T> targetType, ObjectMapper objectMapper) {
    this(
        targetType == null ? null : objectMapper.constructType(targetType.getType()), objectMapper);
  }

  public KafkaJsonSerializer(JavaType targetType, ObjectMapper objectMapper) {
    assert Objects.nonNull(objectMapper);
    this.objectMapper = objectMapper;
    this.writer = objectMapper.writerFor(targetType);
  }

  public boolean isAddTypeInfo() {
    return this.addTypeInfo;
  }

  /**
   * Set false to disable adding type info headers.
   *
   * @param addTypeInfo true to add headers.
   * @since 2.1
   */
  public void setAddTypeInfo(boolean addTypeInfo) {
    this.addTypeInfo = addTypeInfo;
    this.setterCalled = true;
  }

  public Jackson2JavaTypeMapper getTypeMapper() {
    return this.typeMapper;
  }

  /**
   * Set a customized type mapper.
   *
   * @param typeMapper the type mapper.
   * @since 2.1
   */
  public void setTypeMapper(Jackson2JavaTypeMapper typeMapper) {
    assert Objects.nonNull(typeMapper);
    this.typeMapper = typeMapper;
    this.typeMapperExplicitlySet = true;
    this.setterCalled = true;
  }

  /**
   * Configure the default Jackson2JavaTypeMapper to use key type headers.
   *
   * @param isKey Use key type headers if true
   * @since 2.1.3
   */
  public void setUseTypeMapperForKey(boolean isKey) {
    if (!this.typeMapperExplicitlySet && getTypeMapper() instanceof AbstractJavaTypeMapper) {
      ((AbstractJavaTypeMapper) getTypeMapper()).setUseForKey(isKey);
    }
    this.setterCalled = true;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    try {
      this.globalLock.lock();
      if (this.configured) {
        return;
      }
      AssertUtils.state(
          !this.setterCalled
              || (!configs.containsKey(ADD_TYPE_INFO_HEADERS)
                  && !configs.containsKey(TYPE_MAPPINGS)),
          "JsonSerializer must be configured with property setters, or via configuration properties; not both");

      setUseTypeMapperForKey(isKey);
      if (configs.containsKey(ADD_TYPE_INFO_HEADERS)) {
        Object config = configs.get(ADD_TYPE_INFO_HEADERS);
        if (config instanceof Boolean configBoolean) {
          this.addTypeInfo = configBoolean;
        } else if (config instanceof String configString) {
          this.addTypeInfo = Boolean.parseBoolean(configString);
        } else {
          throw new IllegalStateException(ADD_TYPE_INFO_HEADERS + " must be Boolean or String");
        }
      }
      if (configs.containsKey(TYPE_MAPPINGS)
          && !this.typeMapperExplicitlySet
          && this.typeMapper instanceof AbstractJavaTypeMapper abstractJavaTypeMapper) {
        abstractJavaTypeMapper.setIdClassMapping(
            createMappings((String) configs.get(TYPE_MAPPINGS)));
      }
      this.configured = true;
    } finally {
      this.globalLock.unlock();
    }
  }

  protected static Map<String, Class<?>> createMappings(String mappings) {
    Map<String, Class<?>> mappingsMap = new HashMap<>();
    String[] array = StringUtils.commaDelimitedListToStringArray(mappings);
    for (String entry : array) {
      String[] split = entry.split(":");
      if (split.length != 2) {
        throw new IllegalStateException(
            "Each comma-delimited mapping entry must have exactly one ':'");
      }
      try {
        mappingsMap.put(
            split[0].trim(),
            ClassUtils.forName(split[1].trim(), ClassUtils.getDefaultClassLoader()));
      } catch (ClassNotFoundException | LinkageError e) {
        throw new IllegalArgumentException("Failed to load: " + split[1] + " for " + split[0], e);
      }
    }
    return mappingsMap;
  }

  @Override
  public byte[] serialize(String topic, Headers headers, T data) {
    if (data == null) {
      return null;
    }
    if (this.addTypeInfo && headers != null) {
      this.typeMapper.fromJavaType(this.objectMapper.constructType(data.getClass()), headers);
    }
    return serialize(topic, data);
  }

  @Override
  public byte[] serialize(String topic, T data) {
    if (data == null) {
      return null;
    }
    try {
      return this.writer.writeValueAsBytes(data);
    } catch (IOException ex) {
      throw new SerializationException(
          "Can't serialize data [" + data + "] for topic [" + topic + "]", ex);
    }
  }

  @Override
  public void close() {
    // No-op
  }

  /**
   * Copies this serializer with same configuration, except new target type reference is used.
   *
   * @param newTargetType type reference forced for serialization, not null
   * @param <X> new serialization source type
   * @return new instance of serializer with type changes
   * @since 2.6
   */
  public <X> KafkaJsonSerializer<X> copyWithType(Class<? super X> newTargetType) {
    return copyWithType(this.objectMapper.constructType(newTargetType));
  }

  /**
   * Copies this serializer with same configuration, except new target type reference is used.
   *
   * @param newTargetType type reference forced for serialization, not null
   * @param <X> new serialization source type
   * @return new instance of serializer with type changes
   * @since 2.6
   */
  public <X> KafkaJsonSerializer<X> copyWithType(TypeReference<? super X> newTargetType) {
    return copyWithType(this.objectMapper.constructType(newTargetType.getType()));
  }

  /**
   * Copies this serializer with same configuration, except new target java type is used.
   *
   * @param newTargetType java type forced for serialization, not null
   * @param <X> new serialization source type
   * @return new instance of serializer with type changes
   * @since 2.6
   */
  public <X> KafkaJsonSerializer<X> copyWithType(JavaType newTargetType) {
    KafkaJsonSerializer<X> result = new KafkaJsonSerializer<>(newTargetType, this.objectMapper);
    result.addTypeInfo = this.addTypeInfo;
    result.typeMapper = this.typeMapper;
    result.typeMapperExplicitlySet = this.typeMapperExplicitlySet;
    return result;
  }

  // Fluent API

  /**
   * Designate this serializer for serializing keys (default is values); only applies if the default
   * type mapper is used.
   *
   * @return the serializer.
   * @since 2.3
   * @see #setUseTypeMapperForKey(boolean)
   */
  public KafkaJsonSerializer<T> forKeys() {
    setUseTypeMapperForKey(true);
    return this;
  }

  /**
   * Do not include type info headers.
   *
   * @return the serializer.
   * @since 2.3
   * @see #setAddTypeInfo(boolean)
   */
  public KafkaJsonSerializer<T> noTypeInfo() {
    setAddTypeInfo(false);
    return this;
  }

  /**
   * Use the supplied {@link Jackson2JavaTypeMapper}.
   *
   * @param mapper the mapper.
   * @return the serializer.
   * @since 2.3
   * @see #setTypeMapper(Jackson2JavaTypeMapper)
   */
  public KafkaJsonSerializer<T> typeMapper(Jackson2JavaTypeMapper mapper) {
    setTypeMapper(mapper);
    return this;
  }
}
