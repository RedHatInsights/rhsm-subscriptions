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
package com.redhat.swatch.contract.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

class UniqueConstraintViolationsTest {

  @Test
  void recognizesPostgresUniqueViolationSqlState() {
    var sql = new SQLException("duplicate key", "23505");
    var violation =
        new ConstraintViolationException("could not execute statement", sql, "subscription_pkey");
    assertTrue(
        UniqueConstraintViolations.isUniqueConstraintViolation(
            new PersistenceException(violation)));
  }

  @Test
  void ignoresOtherSqlExceptions() {
    var sql = new SQLException("deadlock", "40P01");
    assertFalse(UniqueConstraintViolations.isUniqueConstraintViolation(sql));
  }

  @Test
  void retriesOnceOnUniqueViolation() {
    var sql = new SQLException("duplicate key", "23505");
    var violation =
        new ConstraintViolationException("could not execute statement", sql, "subscription_pkey");
    var attempts = new AtomicInteger();
    UniqueConstraintViolations.runWithRetry(
        () -> {
          if (attempts.getAndIncrement() == 0) {
            throw new PersistenceException(violation);
          }
        });
    assertEquals(2, attempts.get());
  }

  @Test
  void doesNotRetryOtherFailures() {
    assertThrows(
        IllegalStateException.class,
        () ->
            UniqueConstraintViolations.runWithRetry(
                () -> {
                  throw new IllegalStateException("boom");
                }));
  }
}
