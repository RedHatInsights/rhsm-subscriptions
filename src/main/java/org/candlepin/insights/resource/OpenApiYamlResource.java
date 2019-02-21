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

import org.candlepin.insights.api.resources.OpenapiYamlApi;
import org.candlepin.insights.controller.OpenApiSpecController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Serves the OpenAPI spec as /openapi.yaml.
 */
@Component
public class OpenApiYamlResource implements OpenapiYamlApi {
    @Autowired
    OpenApiSpecController controller;

    @Override
    public String getOpenApiYaml() {
        return controller.getOpenApiYaml();
    }
}
