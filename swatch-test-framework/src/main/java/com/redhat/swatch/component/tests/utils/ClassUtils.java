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
package com.redhat.swatch.component.tests.utils;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.groovy.util.concurrent.ConcurrentReferenceHashMap;

public class ClassUtils {
  /** Suffix for array class names: {@code "[]"}. */
  public static final String ARRAY_SUFFIX = "[]";

  /** Prefix for internal array class names: {@code "["}. */
  private static final String INTERNAL_ARRAY_PREFIX = "[";

  /** Prefix for internal non-primitive array class names: {@code "[L"}. */
  private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";

  /** A reusable empty class array constant. */
  private static final Class<?>[] EMPTY_CLASS_ARRAY = {};

  /** The package separator character: {@code '.'}. */
  private static final char PACKAGE_SEPARATOR = '.';

  /** The path separator character: {@code '/'}. */
  private static final char PATH_SEPARATOR = '/';

  /** The nested class separator character: {@code '$'}. */
  private static final char NESTED_CLASS_SEPARATOR = '$';

  /** The CGLIB class separator: {@code "$$"}. */
  public static final String CGLIB_CLASS_SEPARATOR = "$$";

  /** The ".class" file suffix. */
  public static final String CLASS_FILE_SUFFIX = ".class";

  /** Precomputed value for the combination of private, static and final modifiers. */
  private static final int NON_OVERRIDABLE_MODIFIER =
      Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;

  /** Precomputed value for the combination of public and protected modifiers. */
  private static final int OVERRIDABLE_MODIFIER = Modifier.PUBLIC | Modifier.PROTECTED;

  /**
   * Map with primitive wrapper type as key and corresponding primitive type as value, for example:
   * {@code Integer.class -> int.class}.
   */
  private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<>(9);

  /**
   * Map with primitive type as key and corresponding wrapper type as value, for example: {@code
   * int.class -> Integer.class}.
   */
  private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<>(9);

  /**
   * Map with primitive type name as key and corresponding primitive type as value, for example:
   * {@code "int" -> int.class}.
   */
  private static final Map<String, Class<?>> primitiveTypeNameMap = new HashMap<>(32);

  /**
   * Map with common Java language class name as key and corresponding Class as value. Primarily for
   * efficient deserialization of remote invocations.
   */
  private static final Map<String, Class<?>> commonClassCache = new HashMap<>(64);

  /**
   * Common Java language interfaces which are supposed to be ignored when searching for 'primary'
   * user-level interfaces.
   */
  private static final Set<Class<?>> javaLanguageInterfaces;

  /**
   * Cache for equivalent methods on a interface implemented by the declaring class.
   *
   * <p>A {@code null} value signals that no interface method was found for the key.
   */
  private static final Map<Method, Method> interfaceMethodCache =
      new ConcurrentReferenceHashMap<>(256);

  /**
   * Cache for equivalent methods on a public interface implemented by the declaring class.
   *
   * <p>A {@code null} value signals that no public interface method was found for the key.
   *
   * @since 6.2
   */
  private static final Map<Method, Method> publicInterfaceMethodCache =
      new ConcurrentReferenceHashMap<>(256);

  /**
   * Cache for equivalent public methods in a public declaring type within the type hierarchy of the
   * method's declaring class.
   *
   * <p>A {@code null} value signals that no publicly accessible method was found for the key.
   *
   * @since 6.2
   */
  private static final Map<Method, Method> publiclyAccessibleMethodCache =
      new ConcurrentReferenceHashMap<>(256);

  static {
    primitiveWrapperTypeMap.put(Boolean.class, boolean.class);
    primitiveWrapperTypeMap.put(Byte.class, byte.class);
    primitiveWrapperTypeMap.put(Character.class, char.class);
    primitiveWrapperTypeMap.put(Double.class, double.class);
    primitiveWrapperTypeMap.put(Float.class, float.class);
    primitiveWrapperTypeMap.put(Integer.class, int.class);
    primitiveWrapperTypeMap.put(Long.class, long.class);
    primitiveWrapperTypeMap.put(Short.class, short.class);
    primitiveWrapperTypeMap.put(Void.class, void.class);

    // Map entry iteration is less expensive to initialize than forEach with lambdas
    for (Map.Entry<Class<?>, Class<?>> entry : primitiveWrapperTypeMap.entrySet()) {
      primitiveTypeToWrapperMap.put(entry.getValue(), entry.getKey());
      registerCommonClasses(entry.getKey());
    }

    Set<Class<?>> primitiveTypes = new HashSet<>(32);
    primitiveTypes.addAll(primitiveWrapperTypeMap.values());
    Collections.addAll(
        primitiveTypes,
        boolean[].class,
        byte[].class,
        char[].class,
        double[].class,
        float[].class,
        int[].class,
        long[].class,
        short[].class);
    for (Class<?> primitiveType : primitiveTypes) {
      primitiveTypeNameMap.put(primitiveType.getName(), primitiveType);
    }

    registerCommonClasses(
        Boolean[].class,
        Byte[].class,
        Character[].class,
        Double[].class,
        Float[].class,
        Integer[].class,
        Long[].class,
        Short[].class);
    registerCommonClasses(
        Number.class,
        Number[].class,
        String.class,
        String[].class,
        Class.class,
        Class[].class,
        Object.class,
        Object[].class);
    registerCommonClasses(
        Throwable.class,
        Exception.class,
        RuntimeException.class,
        Error.class,
        StackTraceElement.class,
        StackTraceElement[].class);
    registerCommonClasses(
        Enum.class,
        Iterable.class,
        Iterator.class,
        Enumeration.class,
        Collection.class,
        List.class,
        Set.class,
        Map.class,
        Map.Entry.class,
        Optional.class);

    Class<?>[] javaLanguageInterfaceArray = {
      Serializable.class,
      Externalizable.class,
      Closeable.class,
      AutoCloseable.class,
      Cloneable.class,
      Comparable.class
    };
    registerCommonClasses(javaLanguageInterfaceArray);
    javaLanguageInterfaces = Set.of(javaLanguageInterfaceArray);
  }

  private static void registerCommonClasses(Class<?>... commonClasses) {
    for (Class<?> clazz : commonClasses) {
      commonClassCache.put(clazz.getName(), clazz);
    }
  }

  public static Class<?> resolvePrimitiveClassName(String name) {
    Class<?> result = null;
    // Most class names will be quite long, considering that they
    // SHOULD sit in a package, so a length check is worthwhile.
    if (name != null && name.length() <= 7) {
      // Could be a primitive - likely.
      result = primitiveTypeNameMap.get(name);
    }
    return result;
  }

  public static Class<?> forName(String name, ClassLoader classLoader)
      throws ClassNotFoundException, LinkageError {

    assert Objects.nonNull(name);

    Class<?> clazz = resolvePrimitiveClassName(name);
    if (clazz == null) {
      clazz = commonClassCache.get(name);
    }
    if (clazz != null) {
      return clazz;
    }

    // "java.lang.String[]" style arrays
    if (name.endsWith(ARRAY_SUFFIX)) {
      String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
      Class<?> elementClass = forName(elementClassName, classLoader);
      return elementClass.arrayType();
    }

    // "[Ljava.lang.String;" style arrays
    if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(";")) {
      String elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), name.length() - 1);
      Class<?> elementClass = forName(elementName, classLoader);
      return elementClass.arrayType();
    }

    // "[[I" or "[[Ljava.lang.String;" style arrays
    if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
      String elementName = name.substring(INTERNAL_ARRAY_PREFIX.length());
      Class<?> elementClass = forName(elementName, classLoader);
      return elementClass.arrayType();
    }

    ClassLoader clToUse = classLoader;
    if (clToUse == null) {
      clToUse = getDefaultClassLoader();
    }
    try {
      return Class.forName(name, false, clToUse);
    } catch (ClassNotFoundException ex) {
      int lastDotIndex = name.lastIndexOf(PACKAGE_SEPARATOR);
      int previousDotIndex = name.lastIndexOf(PACKAGE_SEPARATOR, lastDotIndex - 1);
      if (lastDotIndex != -1
          && previousDotIndex != -1
          && Character.isUpperCase(name.charAt(previousDotIndex + 1))) {
        String nestedClassName =
            name.substring(0, lastDotIndex)
                + NESTED_CLASS_SEPARATOR
                + name.substring(lastDotIndex + 1);
        try {
          return Class.forName(nestedClassName, false, clToUse);
        } catch (ClassNotFoundException ex2) {
          // Swallow - let original exception get through
        }
      }
      throw ex;
    }
  }

  public static ClassLoader getDefaultClassLoader() {
    ClassLoader cl = null;
    try {
      cl = Thread.currentThread().getContextClassLoader();
    } catch (Throwable ex) {
      // Cannot access thread context ClassLoader - falling back...
    }
    if (cl == null) {
      // No thread context class loader -> use class loader of this class.
      cl = ClassUtils.class.getClassLoader();
      if (cl == null) {
        // getClassLoader() returning null indicates the bootstrap ClassLoader
        try {
          cl = ClassLoader.getSystemClassLoader();
        } catch (Throwable ex) {
          // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
        }
      }
    }
    return cl;
  }

  public static String getPackageName(Class<?> clazz) {
    assert Objects.nonNull(clazz);
    return getPackageName(clazz.getName());
  }

  /**
   * Determine the name of the package of the given fully-qualified class name, for example,
   * "java.lang" for the {@code java.lang.String} class name.
   *
   * @param fqClassName the fully-qualified class name
   * @return the package name, or the empty String if the class is defined in the default package
   */
  public static String getPackageName(String fqClassName) {
    assert Objects.nonNull(fqClassName);
    int lastDotIndex = fqClassName.lastIndexOf(PACKAGE_SEPARATOR);
    return (lastDotIndex != -1 ? fqClassName.substring(0, lastDotIndex) : "");
  }
}
