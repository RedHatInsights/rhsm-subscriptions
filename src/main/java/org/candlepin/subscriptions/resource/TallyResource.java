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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

/**
 * Tally API implementation.
 */
@Component
public class TallyResource implements TallyApi {

    private final ObjectMapper mapper;

    public TallyResource(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private void checkPermission(byte[] xRhIdentity, String accountNumber) {
        try {
            // extract account number from json like {'identity': {'account_number': '12345678'}}"
            Map authObject = mapper.readValue(xRhIdentity, Map.class);
            Map identity = (Map) authObject.getOrDefault("identity", Collections.EMPTY_MAP);
            String authAccountNumber = (String) identity.get("account_number");
            if (!accountNumber.equals(authAccountNumber)) {
                throw new SubscriptionsException(ErrorCode.VALIDATION_FAILED_ERROR,
                    Response.Status.UNAUTHORIZED, "Unauthorized",
                    String.format("%s not authorized to access %s", authAccountNumber, accountNumber));
            }
        }
        catch (IOException e) {
            throw new SubscriptionsException(ErrorCode.REQUEST_PROCESSING_ERROR,
                Response.Status.BAD_REQUEST, "Error reading auth header", e);
        }
    }

    @Override
    public TallyReport getTallyReport(@NotNull byte[] xRhIdentity, String accountNumber, String productId,
        @NotNull String granularity, OffsetDateTime beginning, OffsetDateTime ending) {
        Objects.requireNonNull(accountNumber, "account_number required");
        Objects.requireNonNull(productId, "product_id required");
        Objects.requireNonNull(granularity, "granularity required");
        checkPermission(xRhIdentity, accountNumber);
        return null; // TODO implement
    }
}
