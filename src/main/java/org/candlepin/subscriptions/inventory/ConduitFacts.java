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
package org.candlepin.subscriptions.inventory;

import org.candlepin.subscriptions.utilization.api.model.ConsumerInventory;
import org.candlepin.subscriptions.validator.ip.IpAddress;

import org.hibernate.validator.constraints.Length;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

/**
 * POJO that validates all facts scoped for collection by the conduit.
 */
public class ConduitFacts extends ConsumerInventory {

    @Length(min = 1, max = 255)
    @Override
    public String getFqdn() {
        return super.getFqdn();
    }

    @Override
    @NotNull
    public String getAccountNumber() {
        return super.getAccountNumber();
    }

    @Valid
    @Override
    public List<@IpAddress String> getIpAddresses() {
        return super.getIpAddresses();
    }

    // See https://stackoverflow.com/a/4260512/6124862
    // Also a soft validation.  A mixed delimiter MAC like a1:b2-c3:d4-e5:f6 will still validate
    @Valid
    @Override
    public List<@Pattern(regexp = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$") String> getMacAddresses() {
        return super.getMacAddresses();
    }

    @Positive
    @Override
    public Integer getCpuSockets() {
        return super.getCpuSockets();
    }

    @Positive
    @Override
    public Integer getCpuCores() {
        return super.getCpuCores();
    }

    @Positive
    @Override
    public Long getMemory() {
        return super.getMemory();
    }
}
