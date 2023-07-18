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
import java.util.UUID;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/** A ConstraintValidator that ensures that a string is a valid Leach-Salz UUID */
public class UuidValidator implements ConstraintValidator<Uuid, String> {

  @Override
  public void initialize(Uuid constraintAnnotation) {
    /* intentionally empty */
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // A null or empty value is considered invalid.
    if (value == null || value.isEmpty()) {
      return false;
    }

    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      var hibernateContext = context.unwrap(HibernateConstraintValidatorContext.class);
      hibernateContext
          .addMessageParameter("uuid", value)
          .buildConstraintViolationWithTemplate("{uuid} is not a valid UUID")
          .addConstraintViolation()
          .disableDefaultConstraintViolation();
      return false;
    }
  }
}
