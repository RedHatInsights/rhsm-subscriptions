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
import lombok.Getter;

@Getter
public class MessageValidator<T> {
  private final Predicate<T> filter;
  private final Class<T> type;

  /**
   * Constructs a Filterable object.
   *
   * @param filter the {@code Predicate<T>} to use for filtering.
   * @param type the {@code Class<T>} object representing the type {@code T}.
   */
  public MessageValidator(Predicate<T> filter, Class<T> type) {
    if (filter == null || type == null) {
      throw new IllegalArgumentException("Filter and type must not be null.");
    }
    this.filter = filter;
    this.type = type;
  }

  /**
   * Applies the stored predicate to a given value.
   *
   * @param value The value to test.
   * @return true if the value matches the predicate, false otherwise.
   */
  public boolean test(T value) {
    return filter.test(value);
  }

  /**
   * Gets the Class object for the type T.
   *
   * @return the {@code Class<T>} object.
   */
  public Class<T> getType() {
    return type;
  }

  /**
   * Gets the stored Predicate.
   *
   * @return the {@code Predicate<T>} object.
   */
  public Predicate<T> getFilter() {
    return filter;
  }
}
