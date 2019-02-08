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
package org.candlepin.insights.inventory.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * Factory that produces inventory service clients configured with service URL.
 */
public class ApiClientFactory implements FactoryBean<ApiClient> {
    private static Logger log = LoggerFactory.getLogger(ApiClientFactory.class);

    private String hostInventoryServiceUrl;

    public ApiClientFactory(String hostInventoryServiceUrl) {
        if (hostInventoryServiceUrl != null) {
            log.info("host inventory service URL: {}", hostInventoryServiceUrl);
        }
        else {
            log.info("host inventory service URL not set...");
        }
        this.hostInventoryServiceUrl = hostInventoryServiceUrl;
    }

    @Override
    public ApiClient getObject() throws Exception {
        ApiClient client = new ApiClient();
        if (hostInventoryServiceUrl != null) {
            client.setBasePath(hostInventoryServiceUrl);
        }
        return client;
    }

    @Override
    public Class<?> getObjectType() {
        return ApiClient.class;
    }
}
