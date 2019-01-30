/**
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
package org.candlepin.insights.controller;

import org.candlepin.insights.api.model.Status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Controller use to generate status messages for the StatusResource to use. */
@Component
public class StatusMessageController {
    // Fetches values from the PropertySource defined in ApplicationConfiguration
    @Value("${application.version}")
    private String version;

    public Status createStatus() {
        Status status = new Status();
        status.setVersion(version);
        return status;
    }

}
