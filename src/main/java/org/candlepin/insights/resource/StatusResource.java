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
