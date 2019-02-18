/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.resource;

import org.candlepin.insights.exception.ErrorCode;
import org.candlepin.insights.exception.RhsmConduitException;

import org.springframework.stereotype.Component;

import java.io.InputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * Serves the OpenAPI spec as /openapi.json and /openapi.yaml.
 *
 * These are loaded from the API jar.
 */
@Component
@Path("/")
public class OpenApiSpecResource {
    @GET
    @Path("openapi.json")
    public Response openApiJson() {
        InputStream json = getClass().getClassLoader().getResourceAsStream("openapi.json");
        if (json == null) {
            throw new RhsmConduitException(
                ErrorCode.UNHANDLED_EXCEPTION_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                "Unable to read openapi.json",
                "This should never happen..."
            );
        }
        return Response.ok(json, "application/json").build();
    }

    @GET
    @Path("openapi.yaml")
    public Response openApiYaml() {
        InputStream yaml = getClass().getClassLoader().getResourceAsStream("openapi.yaml");
        if (yaml == null) {
            throw new RhsmConduitException(
                ErrorCode.UNHANDLED_EXCEPTION_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                "Unable to read openapi.yaml",
                "This should never happen..."
            );
        }
        return Response.ok(yaml, "application/x-yaml").build();
    }
}
