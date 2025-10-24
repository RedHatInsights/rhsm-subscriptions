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
import org.apache.kafka.common.header.Headers;

public interface Jackson2JavaTypeMapper {
  enum TypePrecedence {
    /** The type is inferred from the destination method. */
    INFERRED,

    /** The type is obtained from headers. */
    TYPE_ID
  }

  void fromJavaType(JavaType javaType, Headers headers);

  JavaType toJavaType(Headers headers);

  TypePrecedence getTypePrecedence();

  /**
   * Set the precedence for evaluating type information in message properties. When using
   * {@code @KafkaListener} at the method level, the framework attempts to determine the target type
   * for payload conversion from the method signature. If so, this type is provided by the {@code
   * MessagingMessageListenerAdapter}.
   *
   * <p>By default, if the type is concrete (not abstract, not an interface), this will be used
   * ahead of type information provided in the {@code __TypeId__} and associated headers provided by
   * the sender.
   *
   * <p>If you wish to force the use of the {@code __TypeId__} and associated headers (such as when
   * the actual type is a subclass of the method argument type), set the precedence to {@link
   * Jackson2JavaTypeMapper.TypePrecedence#TYPE_ID}.
   *
   * @param typePrecedence the precedence.
   * @since 2.2
   */
  default void setTypePrecedence(TypePrecedence typePrecedence) {
    throw new UnsupportedOperationException("This mapper does not support this method");
  }

  void addTrustedPackages(String... packages);

  /**
   * Remove the type information headers.
   *
   * @param headers the headers.
   * @since 2.2
   */
  default void removeHeaders(Headers headers) {
    // NOSONAR
  }
}
