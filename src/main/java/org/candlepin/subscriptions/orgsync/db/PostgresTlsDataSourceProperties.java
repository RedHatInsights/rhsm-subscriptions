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

package org.candlepin.subscriptions.orgsync.db;

import org.candlepin.subscriptions.validator.VerificationMode;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.validation.constraints.NotBlank;

/**
 * Properties class to hold additional TLS information such as verification mode, root CA location, etc.
 * Generally speaking, the fields in this class correspond one to one with the options on the PostgreSQL
 * JDBC connection URL.  See https://jdbc.postgresql.org/documentation/head/connect.html#ssl
 */
public class PostgresTlsDataSourceProperties extends DataSourceProperties {

    // Require TLS to be actively enabled since doing so requires providing the CA and
    // other settings. No sense in having it be true but then immediately fail due to a missing CA.
    private boolean enableTls = false;

    @VerificationMode
    private String verificationMode = "verify-full";

    @NotBlank
    private String rootCaLocation = "classpath:/rhsm-subscriptions-ca.crt";

    public boolean isEnableTls() {
        return enableTls;
    }

    public void setEnableTls(boolean enableTls) {
        this.enableTls = enableTls;
    }

    public String getVerificationMode() {
        return verificationMode;
    }

    /**
     * Possible values include "disable", "allow", "prefer", "require", "verify-ca" and "verify-full". The
     * "require", "allow", and "prefer" options all default to a non validating SSL factory and do not check
     * the validity of the certificate or the host name. "verify-ca" validates the certificate, but does not
     * verify the hostname. "verify-full" will validate that the certificate is correct and verify the host
     * connected to has the same hostname as the certificate.
     * @param verificationMode the verification mode to use.  One of "disable", "allow", "prefer", "require",
     *      "verify-ca" and "verify-full".
     */
    public void setVerificationMode(String verificationMode) {
        this.verificationMode = verificationMode;
    }

    public String getRootCaLocation() {
        return rootCaLocation;
    }

    public void setRootCaLocation(String rootCaLocation) {
        this.rootCaLocation = rootCaLocation;
    }
}
