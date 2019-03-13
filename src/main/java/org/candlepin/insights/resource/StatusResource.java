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
package org.candlepin.insights.resource;

import org.candlepin.insights.api.model.Readiness;
import org.candlepin.insights.api.model.Status;
import org.candlepin.insights.api.resources.StatusApi;
import org.candlepin.insights.controller.StatusController;
import org.candlepin.insights.exception.NotReadyException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Resource to report on application status. */
@Component
public class StatusResource implements StatusApi {

    @Autowired
    private StatusController statusController;

    /**
     * Also used as a simple liveness probe in OKD.  OKD will call this endpoint periodically to check that
     * the application is still up and running.  If the request fails, OKD will restart the pod.
     */
    @Override
    public Status getStatus() {
        return statusController.createStatus();
    }

    @Override
    public Readiness getReadiness() throws NotReadyException {
        Readiness r = statusController.checkReadiness();
        if (StatusController.AVAILABILITY_OK.equals(r.getAvailability())) {
            return r;
        }
        throw new NotReadyException(r.getAvailability());
    }
}
