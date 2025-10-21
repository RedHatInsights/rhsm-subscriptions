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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.redhat.swatch.component.tests.utils.ClassUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.kafka.common.header.Headers;

public class DefaultJackson2JavaTypeMapper extends AbstractJavaTypeMapper
    implements Jackson2JavaTypeMapper {

  private static final List<String> TRUSTED_PACKAGES =
      List.of("java\\.util\\..*", "java\\.lang\\" + "..*");

  private final Set<String> trustedPackages = new LinkedHashSet<>(TRUSTED_PACKAGES);

  private volatile TypePrecedence typePrecedence = Jackson2JavaTypeMapper.TypePrecedence.INFERRED;

  /**
   * Return the precedence.
   *
   * @return the precedence.
   * @see #setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence)
   */
  @Override
  public TypePrecedence getTypePrecedence() {
    return this.typePrecedence;
  }

  @Override
  public void setTypePrecedence(TypePrecedence typePrecedence) {
    assert Objects.nonNull(typePrecedence);
    this.typePrecedence = typePrecedence;
  }

  /**
   * Specify a set of packages to trust during deserialization. The {@code .*}) means trust all.
   *
   * @param packagesToTrust the trusted Java packages for deserialization
   */
  @Override
  public void addTrustedPackages(String... packagesToTrust) {
    if (this.trustedPackages.isEmpty()) {
      return;
    }
    if (packagesToTrust != null) {
      for (String trusted : packagesToTrust) {
        if (".*".equals(trusted)) {
          this.trustedPackages.clear();
          break;
        } else {
          this.trustedPackages.add(trusted);
        }
      }
    }
  }

  @Override
  public JavaType toJavaType(Headers headers) {
    String typeIdHeader = retrieveHeaderAsString(headers, getClassIdFieldName());

    if (typeIdHeader != null) {

      JavaType classType = getClassIdType(typeIdHeader);
      if (!classType.isContainerType() || classType.isArrayType()) {
        return classType;
      }

      JavaType contentClassType =
          getClassIdType(retrieveHeader(headers, getContentClassIdFieldName()));
      if (classType.getKeyType() == null) {
        return TypeFactory.defaultInstance()
            .constructCollectionLikeType(classType.getRawClass(), contentClassType);
      }

      JavaType keyClassType = getClassIdType(retrieveHeader(headers, getKeyClassIdFieldName()));
      return TypeFactory.defaultInstance()
          .constructMapLikeType(classType.getRawClass(), keyClassType, contentClassType);
    }

    return null;
  }

  private JavaType getClassIdType(String classId) {
    if (getIdClassMapping().containsKey(classId)) {
      return TypeFactory.defaultInstance().constructType(getIdClassMapping().get(classId));
    } else {
      try {
        if (!isTrustedPackage(classId)) {
          throw new IllegalArgumentException(
              "The class '"
                  + classId
                  + "' is not in the trusted packages: "
                  + this.trustedPackages
                  + ". "
                  + "If you believe this class is safe to deserialize, please provide its name. "
                  + "If the serialization is only done by a trusted source, you can also enable "
                  + "trust all (*).");
        } else {
          return TypeFactory.defaultInstance()
              .constructType(ClassUtils.forName(classId, getClassLoader()));
        }
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(
            "failed to resolve class name. Class not found [" + classId + "]", e);
      } catch (LinkageError e) {
        throw new IllegalStateException(
            "failed to resolve class name. Linkage error [" + classId + "]", e);
      }
    }
  }

  private boolean isTrustedPackage(String requestedType) {
    if (!this.trustedPackages.isEmpty()) {
      String packageName = ClassUtils.getPackageName(requestedType).replaceFirst("\\[L", "");
      for (String trustedPackage : this.trustedPackages) {
        if (packageName.matches(trustedPackage)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @Override
  public void fromJavaType(JavaType javaType, Headers headers) {
    String classIdFieldName = getClassIdFieldName();
    if (headers.lastHeader(classIdFieldName) != null) {
      removeHeaders(headers);
    }

    addHeader(headers, classIdFieldName, javaType.getRawClass());

    if (javaType.isContainerType() && !javaType.isArrayType()) {
      addHeader(headers, getContentClassIdFieldName(), javaType.getContentType().getRawClass());
    }

    if (javaType.getKeyType() != null) {
      addHeader(headers, getKeyClassIdFieldName(), javaType.getKeyType().getRawClass());
    }
  }

  public void fromClass(Class<?> clazz, Headers headers) {
    fromJavaType(TypeFactory.defaultInstance().constructType(clazz), headers);
  }

  public Class<?> toClass(Headers headers) {
    return toJavaType(headers).getRawClass();
  }

  @Override
  public void removeHeaders(Headers headers) {
    try {
      headers.remove(getClassIdFieldName());
      headers.remove(getContentClassIdFieldName());
      headers.remove(getKeyClassIdFieldName());
    } catch (Exception e) { // NOSONAR
      // NOSONAR
    }
  }
}
