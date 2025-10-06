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
   * @param filter The Predicate<T> to use for filtering.
   * @param type The Class<T> object representing the type T.
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
   * @return The Class<T> object.
   */
  public Class<T> getType() {
    return type;
  }

  /**
   * Gets the stored Predicate.
   *
   * @return The Predicate<T> object.
   */
  public Predicate<T> getFilter() {
    return filter;
  }

  // Example method to demonstrate using the stored Class object
  public String getTypeName() {
    return type.getName();
  }
}