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

import java.sql.SQLException;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Detects PostgreSQL/H2 unique constraint failures (SQLState 23505) so concurrent consumers can
 * retry instead of holding row locks.
 */
@Slf4j
public final class UniqueConstraintViolations {

  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

  private UniqueConstraintViolations() {}

  public static boolean isUniqueConstraintViolation(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (current instanceof SQLException sql
          && UNIQUE_VIOLATION_SQL_STATE.equals(sql.getSQLState())) {
        return true;
      }
      if (current instanceof ConstraintViolationException hibernate
          && UNIQUE_VIOLATION_SQL_STATE.equals(hibernate.getSQLState())) {
        return true;
      }
    }
    return false;
  }

  public static void runWithRetry(Runnable action) {
    try {
      callWithRetry(
          () -> {
            action.run();
            return null;
          });
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static <T> T callWithRetry(Callable<T> action) throws Exception {
    try {
      return action.call();
    } catch (Exception e) {
      if (!isUniqueConstraintViolation(e)) {
        throw e;
      }
      log.info("Retrying after unique constraint conflict");
      return action.call();
    }
  }
}
