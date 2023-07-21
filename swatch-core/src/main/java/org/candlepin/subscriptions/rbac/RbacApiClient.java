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
package org.candlepin.subscriptions.rbac;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.GenericType;
import java.util.List;
import java.util.Map;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * An extension of the generated RBAC ApiClient that ensures that the appropriate identity header is
 * appended to each request.
 */
public class RbacApiClient extends ApiClient {
  public static final String RH_IDENTITY_HEADER = "x-rh-identity";

  @Override
  public <T> T invokeAPI(
      String path,
      String method,
      List<Pair> queryParams,
      Object body,
      Map<String, String> headerParams,
      Map<String, String> cookieParams,
      Map<String, Object> formParams,
      String accept,
      String contentType,
      String[] authNames,
      GenericType<T> returnType)
      throws ApiException {

    String idHeader = getIdentityHeader();
    if (idHeader == null || idHeader.isEmpty()) {
      throw new BadRequestException("Missing identity header while accessing RBAC service.");
    }

    headerParams.put(RH_IDENTITY_HEADER, idHeader);

    return super.invokeAPI(
        path,
        method,
        queryParams,
        body,
        headerParams,
        cookieParams,
        formParams,
        accept,
        contentType,
        authNames,
        returnType);
  }

  protected static String getIdentityHeader() {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes == null) {
      return null;
    }
    HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
    return request.getHeader(RH_IDENTITY_HEADER);
  }
}
