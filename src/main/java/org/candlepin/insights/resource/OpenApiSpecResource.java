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

import org.candlepin.insights.api.resources.OpenapiJsonApi;
import org.candlepin.insights.api.resources.OpenapiYamlApi;
import org.candlepin.insights.exception.ErrorCode;
import org.candlepin.insights.exception.RhsmConduitException;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.ws.rs.core.Response;

/**
 * Serves the OpenAPI spec as /openapi.json and /openapi.yaml.
 *
 * These are loaded from the API jar.
 */
@Component
public class OpenApiSpecResource implements OpenapiJsonApi, OpenapiYamlApi {

    private String getResourceAsString(String filename) {
        InputStream contents = getClass().getClassLoader().getResourceAsStream(filename);
        if (contents == null) {
            throw new RhsmConduitException(
                ErrorCode.UNHANDLED_EXCEPTION_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                String.format("Unable to read %s", filename),
                "This should never happen..."
            );
        }
        try {
            return IOUtils.toString(contents, Charset.forName("UTF-8"));
        }
        catch (IOException e) {
            throw new RhsmConduitException(
                ErrorCode.UNHANDLED_EXCEPTION_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                String.format("Unable to decode %s", filename),
                "This should never happen..."
            );
        }
    }

    @Override
    public String getOpenApiJson() {
        return getResourceAsString("openapi.json");
    }

    @Override
    public String getOpenApiYaml() {
        return getResourceAsString("openapi.yaml");
    }
}
