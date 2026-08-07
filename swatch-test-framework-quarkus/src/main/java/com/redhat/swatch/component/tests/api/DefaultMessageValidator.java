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
package com.redhat.swatch.component.tests.api;

import java.util.function.Predicate;

public class DefaultMessageValidator<T> extends MessageValidator<String, T> {

  /**
   * Validator that ignores the message key and filters only by value (key is treated as String).
   *
   * @param filter the {@code Predicate<T>} to use for filtering the value.
   * @param valueType the {@code Class<T>} for the message value.
   */
  public DefaultMessageValidator(Predicate<T> filter, Class<T> valueType) {
    super((k, v) -> filter.test(v), String.class, valueType);
  }
}
