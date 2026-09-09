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
package com.redhat.swatch.panache;

import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Programmatic PostgreSQL transactional advisory locks for use inside service methods.
 *
 * <p>Must be called inside an active JTA transaction ({@code pg_advisory_xact_lock} is
 * transaction-scoped).
 */
@ApplicationScoped
@Slf4j
public class TransactionalLocks {

  @Inject
  @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql")
  String dbKind;

  public void acquireLockBy(String prefix, String... keys) {
    if (!isPostgres()) {
      log.trace("Transactional locks are only supported by PostgreSQL");
      return;
    }

    String lockKey = buildLockKey(prefix, keys);
    if (lockKey == null) {
      log.trace("Missing lock key to enable transactional locking. Ignoring");
      return;
    }

    acquireAdvisoryLock(lockKey);
  }

  static String buildLockKey(String prefix, String[] keyParts) {
    if (keyParts == null || keyParts.length == 0) {
      return null;
    }

    String key =
        Arrays.stream(keyParts)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .filter(part -> !part.isBlank())
            .collect(Collectors.joining("-"));

    if (key.isEmpty()) {
      return null;
    }

    return prefix == null || prefix.isBlank() ? key : prefix + "-" + key;
  }

  private void acquireAdvisoryLock(String lockKey) {
    try (var instance = Arc.container().instance(EntityManager.class)) {
      instance
          .get()
          .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(cast(?1 as text)))")
          .setParameter(1, lockKey)
          .getSingleResult();
    }
  }

  private boolean isPostgres() {
    return "postgresql".equalsIgnoreCase(dbKind);
  }
}
