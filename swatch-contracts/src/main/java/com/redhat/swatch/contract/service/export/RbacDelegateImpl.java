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
package com.redhat.swatch.contract.service.export;

import com.redhat.swatch.clients.rbac.RbacApiException;
import com.redhat.swatch.clients.rbac.RbacService;
import com.redhat.swatch.export.ExportServiceException;
import com.redhat.swatch.export.api.RbacDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@ApplicationScoped
public class RbacDelegateImpl implements RbacDelegate {
  private final RbacService rbacService;

  @Override
  public List<String> getPermissions(String application, String xRhIdentity)
      throws ExportServiceException {
    try {
      return rbacService.getPermissions(application, xRhIdentity);
    } catch (RbacApiException e) {
      throw new ExportServiceException(
          Response.Status.NOT_FOUND.getStatusCode(), e.getMessage(), e);
    }
  }
}
