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
package org.candlepin.subscriptions.tally.enrichment;

/**
 * Inventory fact property names used by the enrichers.
 */
public class FactSetProperties {

    // To be consistent, variable names should follow the following format:
    //    <namespace>_myproperty
    public static final String RHSM_RH_PRODUCTS = "RH_PROD";
    public static final String RHSM_CPU_CORES = "CPU_CORES";

    // Yupana facts (QPC)
    public static final String YUPANA_IS_RHEL = "IS_RHEL";

    private FactSetProperties() {
        throw new AssertionError();
    }
}
