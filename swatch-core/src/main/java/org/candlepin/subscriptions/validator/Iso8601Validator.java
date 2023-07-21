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
package org.candlepin.subscriptions.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/** A ConstraintValidator to ensure Strings are in ISO 8601 format. */
public class Iso8601Validator implements ConstraintValidator<Iso8601, String> {

  private Iso8601Format format;

  @Override
  public void initialize(Iso8601 constraintAnnotation) {
    this.format = constraintAnnotation.value();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // Note that the Jakarta Bean Validation specification recommends to consider null values as
    // being valid. If null is not a valid value for an element, it should be annotated with
    // @NotNull explicitly
    if (value == null) {
      return true;
    }

    DateTimeFormatter formatter = format.formatter;

    try {
      formatter.parse(value);
      return true;
    } catch (DateTimeParseException e) {
      HibernateConstraintValidatorContext hibernateContext =
          context.unwrap(HibernateConstraintValidatorContext.class);
      hibernateContext.addMessageParameter("example", format.example);
      return false;
    }
  }
}
