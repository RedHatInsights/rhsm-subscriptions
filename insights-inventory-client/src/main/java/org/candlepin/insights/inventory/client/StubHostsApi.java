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

import org.candlepin.insights.inventory.client.model.BulkHostOut;
import org.candlepin.insights.inventory.client.model.CreateHostIn;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stub of the HostsApi that doesn't make requests, for the methods used by rhsm-conduit.
 */
public class StubHostsApi extends HostsApi {

    private static Logger log = LoggerFactory.getLogger(StubHostsApi.class);

    @Override
    public BulkHostOut apiHostAddHostList(List<CreateHostIn> hosts) throws ApiException {
        log.info("Adding specified hosts to inventory.");
        return new BulkHostOut();
    }
}
