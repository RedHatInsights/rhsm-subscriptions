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
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@TransactionalLock
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class TransactionalLockInterceptor {

  @Inject
  @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql")
  String dbKind;

  @AroundInvoke
  public Object lockAndProceed(InvocationContext context) throws Exception {
    if (!isPostgres()) {
      // database locking is only compatible in PostgreSQL.
      return context.proceed();
    }

    TransactionalLock annotation = context.getMethod().getAnnotation(TransactionalLock.class);
    Object[] args = context.getParameters();

    if (args != null && args.length > 0) {
      Object payload = args[0];
      if (payload == null) {
        // if payload is null, we proceed without locking.
        return context.proceed();
      }

      String key = extractKey(payload, annotation.keyFrom());
      String lockKey = annotation.prefix().isBlank() ? key : annotation.prefix() + "-" + key;

      acquireAdvisoryLock(lockKey);
    }

    return context.proceed();
  }

  private String extractKey(Object payload, String methodName) throws Exception {
    if (methodName != null && !methodName.isBlank()) {
      Method targetMethod = payload.getClass().getMethod(methodName);
      return (String) targetMethod.invoke(payload);
    }

    return payload.toString();
  }

  private void acquireAdvisoryLock(String lockKey) {
    try (var instance = Arc.container().instance(EntityManager.class)) {
      EntityManager entityManager = instance.get();

      entityManager
          .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(cast(?1 as text)))")
          .setParameter(1, lockKey)
          .getSingleResult();
    }
  }

  private boolean isPostgres() {
    return "postgresql".equalsIgnoreCase(dbKind);
  }
}
