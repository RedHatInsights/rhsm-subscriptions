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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.core.Response;

@SpringBootTest
public class OpenApiSpecResourceTest {
    @Autowired
    OpenApiSpecResource resource;

    @Test
    public void testOpenApiJson() throws IOException {
        /* Tests that we receive a successful non-empty response */
        Response response = resource.openApiJson();
        assertEquals(200, response.getStatus());
        InputStream stream = (InputStream) response.getEntity();
        int character = stream.read();
        assertNotEquals(0, character);
    }

    @Test
    public void testOpenApiYaml() throws IOException {
        /* Tests that we receive a successful non-empty response */
        Response response = resource.openApiYaml();
        assertEquals(200, response.getStatus());
        InputStream stream = (InputStream) response.getEntity();
        int character = stream.read();
        assertNotEquals(0, character);
    }
}
