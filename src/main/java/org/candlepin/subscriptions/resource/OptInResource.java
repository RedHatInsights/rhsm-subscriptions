/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.controller.OptInController;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.security.auth.SubscriptionWatchAdminOnly;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.resources.OptInApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;

/**
 * Defines the API for opting an account and its org into the sync/reporting
 * functionality.
 */
@Component
@Profile("api")
public class OptInResource implements OptInApi {

    private OptInController controller;

    @Autowired
    public OptInResource(OptInController controller) {
        this.controller = controller;
    }

    @SubscriptionWatchAdminOnly
    @Override
    public void deleteOptInConfig() {
        controller.optOut(validateAccountNumber(), validateOrgId());
    }

    @SubscriptionWatchAdminOnly
    @Override
    public OptInConfig getOptInConfig() {
        return controller.getOptInConfig(validateAccountNumber(), validateOrgId());
    }

    @SubscriptionWatchAdminOnly
    @Override
    public OptInConfig putOptInConfig(Boolean enableTallySync, Boolean enableTallyReporting,
        Boolean enableConduitSync) {
        // NOTE: All query params are defaulted to 'true' by the API definition, however we
        //       double check below.
        return controller.optIn(
            validateAccountNumber(),
            validateOrgId(),
            OptInType.API,
            trueIfNull(enableTallySync),
            trueIfNull(enableTallyReporting),
            trueIfNull(enableConduitSync)
        );
    }

    private String validateAccountNumber() {
        String accountNumber = ResourceUtils.getAccountNumber();
        if (accountNumber == null) {
            throw new BadRequestException("Must specify an account number.");
        }
        return accountNumber;
    }

    private String validateOrgId() {
        String ownerId = ResourceUtils.getOwnerId();
        if (ownerId == null) {
            throw new BadRequestException("Must specify an org ID.");
        }
        return ownerId;
    }

    private Boolean trueIfNull(Boolean toVerify) {
        if (toVerify == null) {
            return true;
        }
        return toVerify;
    }

}
