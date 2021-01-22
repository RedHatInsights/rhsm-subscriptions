/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import org.postgresql.jdbc.SslMode;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validator to ensure the verification mode option for a postgresql SSL connection is set to an
 * acceptable value. Possible values include "disable", "allow", "prefer", "require", "verify-ca"
 * and "verify-full". The "require", "allow", and "prefer" options all default to a non validating
 * SSL factory and do not check the validity of the certificate or the host name. "verify-ca"
 * validates the certificate, but does not verify the hostname. "verify-full" will validate that the
 * certificate is correct and verify the host connected to has the same hostname as the certificate.
 */
public class VerificationModeValidator implements ConstraintValidator<VerificationMode, String> {
  @Override
  public void initialize(VerificationMode constraintAnnotation) {
    // No-op implementation.
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // A null or empty value will be considered invalid.
    for (SslMode mode : SslMode.values()) {
      if (mode.value.equals(value)) {
        return true;
      }
    }
    return false;
  }
}
