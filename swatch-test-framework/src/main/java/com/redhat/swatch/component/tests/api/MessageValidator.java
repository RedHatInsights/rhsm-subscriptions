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

import java.util.function.BiPredicate;
import lombok.Getter;

@Getter
public class MessageValidator<K, T> {
  private final BiPredicate<K, T> filter;
  private final Class<K> keyType;
  private final Class<T> valueType;

  /**
   * Constructs a MessageValidator that filters by both key and value.
   *
   * @param filter the {@code BiPredicate<K, T>} to use for filtering (key, value).
   * @param keyType the {@code Class<K>} for the message key.
   * @param valueType the {@code Class<T>} for the message value.
   */
  public MessageValidator(BiPredicate<K, T> filter, Class<K> keyType, Class<T> valueType) {
    if (filter == null || keyType == null || valueType == null) {
      throw new IllegalArgumentException("Filter and type must not be null.");
    }
    this.filter = filter;
    this.keyType = keyType;
    this.valueType = valueType;
  }

  /**
   * Applies the stored predicate to a given value.
   *
   * @param key The key to test.
   * @param value The value to test.
   * @return true if the value matches the predicate, false otherwise.
   */
  public boolean test(K key, T value) {
    return filter.test(key, value);
  }
}
