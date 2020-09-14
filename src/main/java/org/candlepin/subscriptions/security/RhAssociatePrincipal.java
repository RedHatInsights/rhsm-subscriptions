/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Red Hat associate authenticated via the x-rh-identity header.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RhAssociatePrincipal implements RhIdentity.Identity {

    /**
     * Container for SAML assertions relayed by the auth gateway.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SamlAssertions {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    @JsonProperty("associate")
    private SamlAssertions samlAssertions;

    public SamlAssertions getSamlAssertions() {
        return samlAssertions;
    }

    public void setSamlAssertions(SamlAssertions samlAssertions) {
        this.samlAssertions = samlAssertions;
    }

    public String getEmail() {
        return samlAssertions.getEmail();
    }

    @Override
    public String toString() {
        return "RhAssociatePrincipal{" + "email='" + getEmail() + '\'' + '}';
    }
}
