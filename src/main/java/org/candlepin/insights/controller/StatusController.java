/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.controller;

import org.candlepin.insights.ApplicationProperties;
import org.candlepin.insights.api.model.Readiness;
import org.candlepin.insights.api.model.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Controller use to generate status messages for the StatusResource to use. */
@Component
public class StatusController {
    public static final String AVAILABILITY_OK = "OK";

    @Autowired
    private ApplicationProperties applicationProperties;

    public Status createStatus() {
        Status status = new Status();
        status.setVersion(applicationProperties.getVersion());
        return status;
    }

    public Readiness checkReadiness() {
        /* TODO:
           Introspect the app to see what the load is.  Do not examine external dependencies.  That can have
           unintended consequences. See https://blog.colinbreck.com/
           kubernetes-liveness-and-readiness-probes-how-to-avoid-shooting-yourself-in-the-foot/
        */
        Readiness r = new Readiness();
        r.setAvailability("OK");
        return r;
    }
}
