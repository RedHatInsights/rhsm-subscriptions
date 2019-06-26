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
package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.resources.TallyApi;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

/**
 * Tally API implementation.
 */
@Component
public class TallyResource implements TallyApi {

    @Context
    SecurityContext securityContext;

    private void checkPermission(String accountNumber) {
        String authAccountNumber = securityContext.getUserPrincipal().getName();
        if (!accountNumber.equals(authAccountNumber)) {
            throw new SubscriptionsException(ErrorCode.VALIDATION_FAILED_ERROR,
                Response.Status.FORBIDDEN, "Unauthorized",
                String.format("%s not authorized to access %s", authAccountNumber, accountNumber));
        }
    }

    @Override
    public TallyReport getTallyReport(String accountNumber, String productId, @NotNull String granularity,
        OffsetDateTime beginning, OffsetDateTime ending) {
        checkPermission(accountNumber);
        return null; // TODO implement
    }
}
