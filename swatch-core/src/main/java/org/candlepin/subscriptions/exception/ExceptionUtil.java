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
package org.candlepin.subscriptions.exception;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import org.candlepin.subscriptions.utilization.api.v1.model.Error;
import org.candlepin.subscriptions.utilization.api.v1.model.Errors;

/** Utility class for constructing the JSON responses to send when exceptions are thrown. */
public class ExceptionUtil {
  // Media type required per the jsonapi.org API spec. JAXRS doesn't provide
  // this type as a constant, so we define it ourselves.
  protected static final String MEDIA_TYPE = "application/vnd.api+json";

  private ExceptionUtil() {
    // only static methods available on this class
  }

  public static Response toResponse(Error error) {
    // IMPL NOTE:
    //   The jsonapi.org spec requires that an Error response should be a
    //   collection of Error objects in a dictionary with an 'errors' key
    //   in case the server should want to return multiple errors in a single
    //   response. While we likely won't ever need to do this, we'll conform
    //   to the spec anyhow.
    Errors errors = new Errors().errors(new ArrayList<>(Collections.singleton(error)));
    return Response.status(Integer.parseInt(error.getStatus()))
        .entity(errors)
        .type(MEDIA_TYPE)
        .build();
  }
}
