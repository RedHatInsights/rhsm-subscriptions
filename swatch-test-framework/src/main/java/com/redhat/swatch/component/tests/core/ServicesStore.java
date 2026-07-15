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
package com.redhat.swatch.component.tests.core;

import com.redhat.swatch.component.tests.api.Service;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ServicesStore implements AutoCloseable {

  private final Map<String, ServiceContext> services = new ConcurrentHashMap<>();

  @Override
  public void close() throws Exception {
    Exception first = null;
    for (ServiceContext context : services.values()) {
      try {
        context.close();
      } catch (Exception e) {
        if (first == null) {
          first = e;
        } else {
          first.addSuppressed(e);
        }
      }
    }
    if (first != null) {
      throw first;
    }
  }

  public List<Service> get() {
    return services.values().stream().map(ServiceContext::getOwner).toList();
  }

  protected ServiceContext getOrCreate(String name, Supplier<ServiceContext> supplier) {
    return services.computeIfAbsent(name, k -> supplier.get());
  }
}
