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
package org.candlepin.subscriptions.resteasy;

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.Principal;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import javax.ws.rs.core.Response;

/**
 * {@link Principal} implementation that parses the x-rh-identity header for account number.
 *
 * Stores the account number as the principal name.
 */
public class IdentityHeaderPrincipal implements Principal {

    private final String accountNumber;

    public IdentityHeaderPrincipal(ObjectMapper mapper, String header) {
        // extract account number from json like {"identity":{"account_number":"12345678"}}
        try {
            byte[] decoded = Base64.getDecoder().decode(header);
            Map authObject = mapper.readValue(decoded, Map.class);
            Map identity = (Map) authObject.getOrDefault("identity", Collections.emptyMap());
            this.accountNumber = (String) identity.get("account_number");
            if (accountNumber == null) {
                throw new IllegalArgumentException("No account number in auth header");
            }
        }
        catch (IOException | IllegalArgumentException e) {
            throw new SubscriptionsException(
                ErrorCode.VALIDATION_FAILED_ERROR,
                Response.Status.BAD_REQUEST,
                "error processing identity header",
                e
            );
        }
    }

    @Override
    public String getName() {
        return accountNumber;
    }
}
