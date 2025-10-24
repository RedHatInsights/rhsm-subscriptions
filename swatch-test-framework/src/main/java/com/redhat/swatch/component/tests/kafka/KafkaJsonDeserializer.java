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
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.redhat.swatch.component.tests.kafka.Jackson2JavaTypeMapper.TypePrecedence;
import com.redhat.swatch.component.tests.utils.AssertUtils;
import com.redhat.swatch.component.tests.utils.ClassUtils;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.component.tests.utils.StringUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.testcontainers.shaded.com.google.common.reflect.TypeToken;

/**
 * Generic {@link org.apache.kafka.common.serialization.Deserializer Deserializer} for receiving
 * JSON from Kafka and return Java objects.
 *
 * <p>IMPORTANT: Configuration must be done completely with property setters or via {@link
 * #configure(Map, boolean)}, not a mixture. If any setters have been called, {@link #configure(Map,
 * boolean)} will be a no-op.
 *
 * @param <T> class of the entity, representing messages
 */
public class KafkaJsonDeserializer<T> implements Deserializer<T> {
  /** Kafka config property for the default key type if no header. */
  public static final String KEY_DEFAULT_TYPE = "swatch.json.key.default.type";

  /** Kafka config property for the default value type if no header. */
  public static final String VALUE_DEFAULT_TYPE = "swatch.json.value.default.type";

  /** Kafka config property for trusted deserialization packages. */
  public static final String TRUSTED_PACKAGES = "swatch.json.trusted.packages";

  /** Kafka config property to add type mappings to the type mapper: 'foo=com.Foo,bar=com.Bar'. */
  public static final String TYPE_MAPPINGS = KafkaJsonSerializer.TYPE_MAPPINGS;

  /** Kafka config property for removing type headers (default true). */
  public static final String REMOVE_TYPE_INFO_HEADERS = "spring.json.remove.type.headers";

  /**
   * Kafka config property for using type headers (default true).
   *
   * @since 2.2.3
   */
  public static final String USE_TYPE_INFO_HEADERS = "spring.json.use.type.headers";

  /**
   * A method name to determine the {@link JavaType} to deserialize the key to:
   * 'com.Foo.deserialize'. See {@link JsonTypeResolver#resolveType} for the signature.
   */
  public static final String KEY_TYPE_METHOD = "spring.json.key.type.method";

  /**
   * A method name to determine the {@link JavaType} to deserialize the value to:
   * 'com.Foo.deserialize'. See {@link JsonTypeResolver#resolveType} for the signature.
   */
  public static final String VALUE_TYPE_METHOD = "spring.json.value.type.method";

  private static final Set<String> OUR_KEYS = new HashSet<>();

  static {
    OUR_KEYS.add(KEY_DEFAULT_TYPE);
    OUR_KEYS.add(VALUE_DEFAULT_TYPE);
    OUR_KEYS.add(TRUSTED_PACKAGES);
    OUR_KEYS.add(TYPE_MAPPINGS);
    OUR_KEYS.add(REMOVE_TYPE_INFO_HEADERS);
    OUR_KEYS.add(USE_TYPE_INFO_HEADERS);
    OUR_KEYS.add(KEY_TYPE_METHOD);
    OUR_KEYS.add(VALUE_TYPE_METHOD);
  }

  protected final ObjectMapper objectMapper; // NOSONAR

  protected JavaType targetType; // NOSONAR

  protected Jackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper(); // NOSONAR

  private ObjectReader reader;

  private boolean typeMapperExplicitlySet = false;

  private boolean removeTypeHeaders = true;

  private boolean useTypeHeaders = true;

  private JsonTypeResolver typeResolver;

  private boolean setterCalled;

  private boolean configured;

  private final Lock trustedPackagesLock = new ReentrantLock();

  /** Construct an instance with a default {@link ObjectMapper}. */
  public KafkaJsonDeserializer() {
    this((Class<T>) null, true);
  }

  /**
   * Construct an instance with the provided {@link ObjectMapper}.
   *
   * @param objectMapper a custom object mapper.
   */
  public KafkaJsonDeserializer(ObjectMapper objectMapper) {
    this((Class<T>) null, objectMapper, true);
  }

  /**
   * Construct an instance with the provided target type, and a default {@link ObjectMapper}.
   *
   * @param targetType the target type to use if no type info headers are present.
   */
  public KafkaJsonDeserializer(Class<? super T> targetType) {
    this(targetType, true);
  }

  /**
   * Construct an instance with the provided target type, and a default {@link ObjectMapper}.
   *
   * @param targetType the target type reference to use if no type info headers are present.
   * @since 2.3
   */
  public KafkaJsonDeserializer(TypeReference<? super T> targetType) {
    this(targetType, true);
  }

  /**
   * Construct an instance with the provided target type, and a default {@link ObjectMapper}.
   *
   * @param targetType the target java type to use if no type info headers are present.
   * @since 2.3
   */
  public KafkaJsonDeserializer(JavaType targetType) {
    this(targetType, true);
  }

  /**
   * Construct an instance with the provided target type, and useHeadersIfPresent with a default
   * {@link ObjectMapper}.
   *
   * @param targetType the target type.
   * @param useHeadersIfPresent true to use headers if present and fall back to target type if not.
   * @since 2.2
   */
  public KafkaJsonDeserializer(Class<? super T> targetType, boolean useHeadersIfPresent) {
    this(targetType, JsonUtils.getObjectMapper(), useHeadersIfPresent);
  }

  /**
   * Construct an instance with the provided target type, and useHeadersIfPresent with a default
   * {@link ObjectMapper}.
   *
   * @param targetType the target type reference.
   * @param useHeadersIfPresent true to use headers if present and fall back to target type if not.
   * @since 2.3
   */
  public KafkaJsonDeserializer(TypeReference<? super T> targetType, boolean useHeadersIfPresent) {
    this(targetType, JsonUtils.getObjectMapper(), useHeadersIfPresent);
  }

  /**
   * Construct an instance with the provided target type, and useHeadersIfPresent with a default
   * {@link ObjectMapper}.
   *
   * @param targetType the target java type.
   * @param useHeadersIfPresent true to use headers if present and fall back to target type if not.
   * @since 2.3
   */
  public KafkaJsonDeserializer(JavaType targetType, boolean useHeadersIfPresent) {
    this(targetType, JsonUtils.getObjectMapper(), useHeadersIfPresent);
  }

  /**
   * Construct an instance with the provided target type, and {@link ObjectMapper}.
   *
   * @param targetType the target type to use if no type info headers are present.
   * @param objectMapper the mapper. type if not.
   */
  public KafkaJsonDeserializer(Class<? super T> targetType, ObjectMapper objectMapper) {
    this(targetType, objectMapper, true);
  }

  /**
   * Construct an instance with the provided target type, and {@link ObjectMapper}.
   *
   * @param targetType the target type reference to use if no type info headers are present.
   * @param objectMapper the mapper. type if not.
   */
  public KafkaJsonDeserializer(TypeReference<? super T> targetType, ObjectMapper objectMapper) {
    this(targetType, objectMapper, true);
  }

  /**
   * Construct an instance with the provided target type, and {@link ObjectMapper}.
   *
   * @param targetType the target java type to use if no type info headers are present.
   * @param objectMapper the mapper. type if not.
   */
  public KafkaJsonDeserializer(JavaType targetType, ObjectMapper objectMapper) {
    this(targetType, objectMapper, true);
  }

  /**
   * Construct an instance with the provided target type, {@link ObjectMapper} and
   * useHeadersIfPresent.
   *
   * @param targetType the target type.
   * @param objectMapper the mapper.
   * @param useHeadersIfPresent true to use headers if present and fall back to target type if not.
   * @since 2.2
   */
  public KafkaJsonDeserializer(
      Class<? super T> targetType, ObjectMapper objectMapper, boolean useHeadersIfPresent) {

    AssertUtils.notNull(objectMapper, "'objectMapper' must not be null.");
    this.objectMapper = objectMapper;
    JavaType javaType = null;
    if (targetType == null) {
      // Use Guava's TypeToken to get the direct supertype and resolve first generic parameter
      @SuppressWarnings("unchecked")
      TypeToken<KafkaJsonDeserializer<T>> typeToken =
          (TypeToken<KafkaJsonDeserializer<T>>) TypeToken.of(getClass());
      TypeToken<?> supertype = typeToken.getSupertype(KafkaJsonDeserializer.class);
      java.lang.reflect.Type resolvedType = supertype.getType();

      if (resolvedType instanceof java.lang.reflect.ParameterizedType) {
        java.lang.reflect.ParameterizedType parameterizedType =
            (java.lang.reflect.ParameterizedType) resolvedType;
        java.lang.reflect.Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length > 0) {
          Class<?> genericType = TypeToken.of(typeArguments[0]).getRawType();
          // If genericType is of type Object, null will be returned.  In the initialize method,
          // the type may be null if useHeadersIfPresent is true.  Otherwise, an assertion will
          // fail.
          if (genericType != Object.class) {
            javaType = TypeFactory.defaultInstance().constructType(genericType);
          }
        }
      }
    } else {
      javaType = TypeFactory.defaultInstance().constructType(targetType);
    }

    initialize(javaType, useHeadersIfPresent);
  }

  /**
   * Construct an instance with the provided target type, {@link ObjectMapper} and
   * useHeadersIfPresent.
   *
   * @param targetType the target type reference.
   * @param objectMapper the mapper.
   * @param useHeadersIfPresent true to use headers if present and fall back to target type if not.
   * @since 2.3
   */
  public KafkaJsonDeserializer(
      TypeReference<? super T> targetType, ObjectMapper objectMapper, boolean useHeadersIfPresent) {

    this(
        targetType != null ? TypeFactory.defaultInstance().constructType(targetType) : null,
        objectMapper,
        useHeadersIfPresent);
  }

  /**
   * Construct an instance with the provided target type, {@link ObjectMapper} and
   * useHeadersIfPresent.
   *
   * @param targetType the target type reference.
   * @param objectMapper the mapper.
   * @param useHeadersIfPresent true to use headers if present and fall back to target type if not.
   * @since 2.3
   */
  public KafkaJsonDeserializer(
      JavaType targetType, ObjectMapper objectMapper, boolean useHeadersIfPresent) {

    AssertUtils.notNull(objectMapper, "'objectMapper' must not be null.");
    this.objectMapper = objectMapper;
    initialize(targetType, useHeadersIfPresent);
  }

  public Jackson2JavaTypeMapper getTypeMapper() {
    return this.typeMapper;
  }

  /**
   * Set a customized type mapper. If the mapper is an {@link AbstractJavaTypeMapper}, any class
   * mappings configured in the mapper will be added to the trusted packages.
   *
   * @param typeMapper the type mapper.
   * @since 2.1
   */
  public void setTypeMapper(Jackson2JavaTypeMapper typeMapper) {
    AssertUtils.notNull(objectMapper, "'objectMapper' must not be null.");
    this.typeMapper = typeMapper;
    this.typeMapperExplicitlySet = true;
    if (typeMapper instanceof AbstractJavaTypeMapper) {
      addMappingsToTrusted(((AbstractJavaTypeMapper) typeMapper).getIdClassMapping());
    }
    this.setterCalled = true;
  }

  /**
   * Configure the default Jackson2JavaTypeMapper to use key type headers.
   *
   * @param isKey Use key type headers if true
   * @since 2.1.3
   */
  public void setUseTypeMapperForKey(boolean isKey) {
    doSetUseTypeMapperForKey(isKey);
    this.setterCalled = true;
  }

  private void doSetUseTypeMapperForKey(boolean isKey) {
    if (!this.typeMapperExplicitlySet && this.getTypeMapper() instanceof AbstractJavaTypeMapper) {
      ((AbstractJavaTypeMapper) this.getTypeMapper()).setUseForKey(isKey);
    }
  }

  /**
   * Set to false to retain type information headers after deserialization. Default true.
   *
   * @param removeTypeHeaders true to remove headers.
   * @since 2.2
   */
  public void setRemoveTypeHeaders(boolean removeTypeHeaders) {
    this.removeTypeHeaders = removeTypeHeaders;
    this.setterCalled = true;
  }

  /**
   * Set to false to ignore type information in headers and use the configured target type instead.
   * Only applies if the preconfigured type mapper is used. Default true.
   *
   * @param useTypeHeaders false to ignore type headers.
   * @since 2.2.8
   */
  public void setUseTypeHeaders(boolean useTypeHeaders) {
    if (!this.typeMapperExplicitlySet) {
      this.useTypeHeaders = useTypeHeaders;
      setUpTypePrecedence(Collections.emptyMap());
    }
    this.setterCalled = true;
  }

  /**
   * Set a {@link BiFunction} that receives the data to be deserialized and the headers and returns
   * a JavaType.
   *
   * @param typeFunction the function.
   * @since 2.5
   */
  public void setTypeFunction(BiFunction<byte[], Headers, JavaType> typeFunction) {
    this.typeResolver = (topic, data, headers) -> typeFunction.apply(data, headers);
    this.setterCalled = true;
  }

  /**
   * Set a {@link JsonTypeResolver} that receives the data to be deserialized and the headers and
   * returns a JavaType.
   *
   * @param typeResolver the resolver.
   * @since 2.5.3
   */
  public void setTypeResolver(JsonTypeResolver typeResolver) {
    this.typeResolver = typeResolver;
    this.setterCalled = true;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    try {
      this.trustedPackagesLock.lock();
      if (this.configured) {
        return;
      }
      AssertUtils.state(
          !this.setterCalled || !configsHasOurKeys(configs),
          "JsonDeserializer must be configured with property setters, or via configuration properties; not both");
      doSetUseTypeMapperForKey(isKey);
      setUpTypePrecedence(configs);
      setupTarget(configs, isKey);
      if (configs.containsKey(TRUSTED_PACKAGES)
          && configs.get(TRUSTED_PACKAGES) instanceof String) {
        this.typeMapper.addTrustedPackages(
            StringUtils.delimitedListToStringArray(
                (String) configs.get(TRUSTED_PACKAGES), ",", " \r\n\f\t"));
      }
      if (configs.containsKey(TYPE_MAPPINGS)
          && !this.typeMapperExplicitlySet
          && this.typeMapper instanceof AbstractJavaTypeMapper) {
        ((AbstractJavaTypeMapper) this.typeMapper).setIdClassMapping(createMappings(configs));
      }
      if (configs.containsKey(REMOVE_TYPE_INFO_HEADERS)) {
        this.removeTypeHeaders =
            Boolean.parseBoolean(configs.get(REMOVE_TYPE_INFO_HEADERS).toString());
      }
      setUpTypeMethod(configs, isKey);
      this.configured = true;
    } finally {
      this.trustedPackagesLock.unlock();
    }
  }

  private boolean configsHasOurKeys(Map<String, ?> configs) {
    for (String key : configs.keySet()) {
      if (OUR_KEYS.contains(key)) {
        return true;
      }
    }
    return false;
  }

  private Map<String, Class<?>> createMappings(Map<String, ?> configs) {
    Map<String, Class<?>> mappings =
        KafkaJsonSerializer.createMappings(
            configs.get(KafkaJsonSerializer.TYPE_MAPPINGS).toString());
    addMappingsToTrusted(mappings);
    return mappings;
  }

  private void setUpTypeMethod(Map<String, ?> configs, boolean isKey) {
    if (isKey && configs.containsKey(KEY_TYPE_METHOD)) {
      setUpTypeResolver((String) configs.get(KEY_TYPE_METHOD));
    } else if (!isKey && configs.containsKey(VALUE_TYPE_METHOD)) {
      setUpTypeResolver((String) configs.get(VALUE_TYPE_METHOD));
    }
  }

  private static <P, T> BiFunction<P, Headers, T> propertyToMethodInvokingFunction(
      String methodProperty, Class<P> payloadType, ClassLoader classLoader) {
    int lastDotPosn = methodProperty.lastIndexOf('.');
    AssertUtils.state(
        lastDotPosn > 1,
        "the method property needs to be a class name followed by the method name, separated by '.'");
    BiFunction<P, Headers, T> function;
    Class<?> clazz;
    try {
      clazz = ClassUtils.forName(methodProperty.substring(0, lastDotPosn), classLoader);
    } catch (ClassNotFoundException | LinkageError e) {
      throw new IllegalStateException(e);
    }
    String methodName = methodProperty.substring(lastDotPosn + 1);
    Method method;
    try {
      method = clazz.getDeclaredMethod(methodName, payloadType, Headers.class);
    } catch (
        @SuppressWarnings("unused")
        NoSuchMethodException e) {
      try {
        method = clazz.getDeclaredMethod(methodName, payloadType);
      } catch (
          @SuppressWarnings("unused")
          NoSuchMethodException e1) {
        IllegalStateException ise =
            new IllegalStateException(
                "the parser method must take '("
                    + payloadType.getSimpleName()
                    + ", Headers)' or '("
                    + payloadType.getSimpleName()
                    + ")'",
                e1);
        ise.addSuppressed(e);
        throw ise; // NOSONAR, lost stack trace
      } catch (SecurityException e1) {
        IllegalStateException ise = new IllegalStateException(e1);
        ise.addSuppressed(e);
        throw ise; // NOSONAR, lost stack trace
      }
    } catch (SecurityException e) {
      throw new IllegalStateException(e);
    }
    Method parseMethod = method;
    if (method.getParameters().length > 1) {
      function =
          (str, headers) -> {
            try {
              return (T) parseMethod.invoke(null, str, headers);
            } catch (IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {
              throw new IllegalStateException(e);
            }
          };
    } else {
      function =
          (str, headers) -> {
            try {
              return (T) parseMethod.invoke(null, str);
            } catch (IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {
              throw new IllegalStateException(e);
            }
          };
    }
    return function;
  }

  private void setUpTypeResolver(String method) {
    try {
      this.typeResolver = buildTypeResolver(method);
    } catch (IllegalStateException e) {
      if (e.getCause() instanceof NoSuchMethodException) {
        this.typeResolver =
            (topic, data, headers) ->
                (JavaType)
                    propertyToMethodInvokingFunction(
                            method, byte[].class, getClass().getClassLoader())
                        .apply(data, headers);
        return;
      }
      throw e;
    }
  }

  private void setUpTypePrecedence(Map<String, ?> configs) {
    if (!this.typeMapperExplicitlySet) {
      if (configs.containsKey(USE_TYPE_INFO_HEADERS)) {
        this.useTypeHeaders = Boolean.parseBoolean(configs.get(USE_TYPE_INFO_HEADERS).toString());
      }
      this.typeMapper.setTypePrecedence(
          this.useTypeHeaders ? TypePrecedence.TYPE_ID : TypePrecedence.INFERRED);
    }
  }

  private void setupTarget(Map<String, ?> configs, boolean isKey) {
    try {
      JavaType javaType = null;
      if (isKey && configs.containsKey(KEY_DEFAULT_TYPE)) {
        javaType = setupTargetType(configs, KEY_DEFAULT_TYPE);
      } else if (!isKey && configs.containsKey(VALUE_DEFAULT_TYPE)) {
        javaType = setupTargetType(configs, VALUE_DEFAULT_TYPE);
      }

      if (javaType != null) {
        initialize(javaType, TypePrecedence.TYPE_ID.equals(this.typeMapper.getTypePrecedence()));
      }
    } catch (ClassNotFoundException | LinkageError e) {
      throw new IllegalStateException(e);
    }
  }

  private void initialize(JavaType type, boolean useHeadersIfPresent) {
    this.targetType = type;
    this.useTypeHeaders = useHeadersIfPresent;
    AssertUtils.isTrue(
        this.targetType != null || useHeadersIfPresent,
        "'targetType' cannot be null if 'useHeadersIfPresent' is false");

    if (this.targetType != null) {
      this.reader = this.objectMapper.readerFor(this.targetType);
    }

    addTargetPackageToTrusted();
    this.typeMapper.setTypePrecedence(
        useHeadersIfPresent ? TypePrecedence.TYPE_ID : TypePrecedence.INFERRED);
  }

  private JavaType setupTargetType(Map<String, ?> configs, String key)
      throws ClassNotFoundException, LinkageError {
    if (configs.get(key) instanceof Class) {
      return TypeFactory.defaultInstance().constructType((Class<?>) configs.get(key));
    } else if (configs.get(key) instanceof String) {
      return TypeFactory.defaultInstance()
          .constructType(ClassUtils.forName((String) configs.get(key), null));
    } else {
      throw new IllegalStateException(key + " must be Class or String");
    }
  }

  /**
   * Add trusted packages for deserialization.
   *
   * @param packages the packages.
   * @since 2.1
   */
  public void addTrustedPackages(String... packages) {
    try {
      this.trustedPackagesLock.lock();
      doAddTrustedPackages(packages);
      this.setterCalled = true;
    } finally {
      this.trustedPackagesLock.unlock();
    }
  }

  private void addMappingsToTrusted(Map<String, Class<?>> mappings) {
    mappings
        .values()
        .forEach(
            clazz -> {
              String packageName =
                  clazz.isArray()
                      ? clazz.getComponentType().getPackage().getName()
                      : clazz.getPackage().getName();
              doAddTrustedPackages(packageName);
              doAddTrustedPackages(packageName + ".*");
            });
  }

  private void addTargetPackageToTrusted() {
    String targetPackageName = getTargetPackageName();
    if (targetPackageName != null) {
      doAddTrustedPackages(targetPackageName);
      doAddTrustedPackages(targetPackageName + ".*");
    }
  }

  private String getTargetPackageName() {
    if (this.targetType != null) {
      return ClassUtils.getPackageName(this.targetType.getRawClass()).replaceFirst("\\[L", "");
    }
    return null;
  }

  private void doAddTrustedPackages(String... packages) {
    this.typeMapper.addTrustedPackages(packages);
  }

  @Override
  public T deserialize(String topic, Headers headers, byte[] data) {
    if (data == null) {
      return null;
    }
    ObjectReader deserReader = null;
    JavaType javaType = null;
    if (this.typeResolver != null) {
      javaType = this.typeResolver.resolveType(topic, data, headers);
    }
    if (javaType == null && this.typeMapper.getTypePrecedence().equals(TypePrecedence.TYPE_ID)) {
      javaType = this.typeMapper.toJavaType(headers);
    }
    if (javaType != null) {
      deserReader = this.objectMapper.readerFor(javaType);
    }
    if (this.removeTypeHeaders) {
      this.typeMapper.removeHeaders(headers);
    }
    if (deserReader == null) {
      deserReader = this.reader;
    }
    AssertUtils.state(
        deserReader != null, "No type information in headers and no default type provided");
    try {
      return deserReader.readValue(data);
    } catch (IOException ex) {
      throw new SerializationException("Can't deserialize data  from topic [" + topic + "]", ex);
    }
  }

  @Override
  public T deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    ObjectReader localReader = this.reader;
    if (this.typeResolver != null) {
      JavaType javaType = this.typeResolver.resolveType(topic, data, null);
      if (javaType != null) {
        localReader = this.objectMapper.readerFor(javaType);
      }
    }
    AssertUtils.state(localReader != null, "No headers available and no default type provided");
    try {
      return localReader.readValue(data);
    } catch (IOException e) {
      throw new SerializationException(
          "Can't deserialize data [" + Arrays.toString(data) + "] from topic [" + topic + "]", e);
    }
  }

  @Override
  public void close() {
    // No-op
  }

  /**
   * Copies this deserializer with same configuration, except new target type is used.
   *
   * @param newTargetType type used for when type headers are missing, not null
   * @param <X> new deserialization result type
   * @return new instance of deserializer with type changes
   * @since 2.6
   */
  public <X> KafkaJsonDeserializer<X> copyWithType(Class<? super X> newTargetType) {
    return copyWithType(this.objectMapper.constructType(newTargetType));
  }

  /**
   * Copies this deserializer with same configuration, except new target type reference is used.
   *
   * @param newTargetType type reference used for when type headers are missing, not null
   * @param <X> new deserialization result type
   * @return new instance of deserializer with type changes
   * @since 2.6
   */
  public <X> KafkaJsonDeserializer<X> copyWithType(TypeReference<? super X> newTargetType) {
    return copyWithType(this.objectMapper.constructType(newTargetType.getType()));
  }

  /**
   * Copies this deserializer with same configuration, except new target java type is used.
   *
   * @param newTargetType java type used for when type headers are missing, not null
   * @param <X> new deserialization result type
   * @return new instance of deserializer with type changes
   * @since 2.6
   */
  public <X> KafkaJsonDeserializer<X> copyWithType(JavaType newTargetType) {
    KafkaJsonDeserializer<X> result =
        new KafkaJsonDeserializer<>(newTargetType, this.objectMapper, this.useTypeHeaders);
    result.removeTypeHeaders = this.removeTypeHeaders;
    result.typeMapper = this.typeMapper;
    result.typeMapperExplicitlySet = this.typeMapperExplicitlySet;
    return result;
  }

  // Fluent API

  /**
   * Designate this deserializer for deserializing keys (default is values); only applies if the
   * default type mapper is used.
   *
   * @return the deserializer.
   * @since 2.3
   */
  public KafkaJsonDeserializer<T> forKeys() {
    setUseTypeMapperForKey(true);
    return this;
  }

  /**
   * Don't remove type information headers.
   *
   * @return the deserializer.
   * @since 2.3
   * @see #setRemoveTypeHeaders(boolean)
   */
  public KafkaJsonDeserializer<T> dontRemoveTypeHeaders() {
    setRemoveTypeHeaders(false);
    return this;
  }

  /**
   * Ignore type information headers and use the configured target class.
   *
   * @return the deserializer.
   * @since 2.3
   * @see #setUseTypeHeaders(boolean)
   */
  public KafkaJsonDeserializer<T> ignoreTypeHeaders() {
    setUseTypeHeaders(false);
    return this;
  }

  /**
   * Use the supplied {@link Jackson2JavaTypeMapper}.
   *
   * @param mapper the mapper.
   * @return the deserializer.
   * @since 2.3
   * @see #setTypeMapper(Jackson2JavaTypeMapper)
   */
  public KafkaJsonDeserializer<T> typeMapper(Jackson2JavaTypeMapper mapper) {
    setTypeMapper(mapper);
    return this;
  }

  /**
   * Add trusted packages to the default type mapper.
   *
   * @param packages the packages.
   * @return the deserializer.
   * @since 2,5
   */
  public KafkaJsonDeserializer<T> trustedPackages(String... packages) {
    try {
      this.trustedPackagesLock.lock();
      AssertUtils.isTrue(
          !this.typeMapperExplicitlySet,
          "When using a custom type mapper, set the " + "trusted packages there");
      this.typeMapper.addTrustedPackages(packages);
      return this;
    } finally {
      this.trustedPackagesLock.unlock();
    }
  }

  /**
   * Set a {@link BiFunction} that receives the data to be deserialized and the headers and returns
   * a JavaType.
   *
   * @param typeFunction the function.
   * @return the deserializer.
   * @since 2.5
   */
  public KafkaJsonDeserializer<T> typeFunction(BiFunction<byte[], Headers, JavaType> typeFunction) {
    setTypeFunction(typeFunction);
    return this;
  }

  /**
   * Set a {@link JsonTypeResolver} that receives the data to be deserialized and the headers and
   * returns a JavaType.
   *
   * @param resolver the resolver.
   * @return the deserializer.
   * @since 2.5.3
   */
  public KafkaJsonDeserializer<T> typeResolver(JsonTypeResolver resolver) {
    setTypeResolver(resolver);
    return this;
  }

  private JsonTypeResolver buildTypeResolver(String methodProperty) {
    int lastDotPosn = methodProperty.lastIndexOf('.');
    AssertUtils.state(
        lastDotPosn > 1,
        "the method property needs to be a class name followed by the method name, separated by '.'");
    Class<?> clazz;
    try {
      clazz =
          ClassUtils.forName(methodProperty.substring(0, lastDotPosn), getClass().getClassLoader());
    } catch (ClassNotFoundException | LinkageError e) {
      throw new IllegalStateException(e);
    }
    String methodName = methodProperty.substring(lastDotPosn + 1);
    Method method;
    try {
      method = clazz.getDeclaredMethod(methodName, String.class, byte[].class, Headers.class);
      AssertUtils.state(
          JavaType.class.isAssignableFrom(method.getReturnType()),
          method + " return type must be JavaType");
      AssertUtils.state(Modifier.isStatic(method.getModifiers()), method + " must be static");
    } catch (SecurityException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
    return (topic, data, headers) -> {
      try {
        return (JavaType) method.invoke(null, topic, data, headers);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        throw new IllegalStateException(e);
      }
    };
  }
}
