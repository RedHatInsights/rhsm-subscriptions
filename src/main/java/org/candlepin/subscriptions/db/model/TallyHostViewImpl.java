/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.db.model;

import static java.util.Objects.*;

import org.springframework.beans.BeanUtils;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of TallyHostView to use instead of projections
 */
@Getter
@Setter
public class TallyHostViewImpl implements TallyHostView {
    private String inventoryId;
    private String insightsId;
    private String displayName;
    private String hardwareType;
    private String hardwareMeasurementType;
    private int cores;
    private int sockets;
    private Integer numOfGuests;
    private String subscriptionManagerId;
    private OffsetDateTime lastSeen;
    private boolean unmappedGuest;
    private boolean isHypervisor;
    private String cloudProvider;

    public TallyHostViewImpl(Host host) {
        BeanUtils.copyProperties(host, this);

        /*
         * Theoretically we should only have one bucket returning that met the criteria of the query
         */
        HostTallyBucket firstBucket = host.getBuckets().stream().findFirst().orElseThrow();

        this.setCores(firstBucket.getCores());
        this.setSockets(firstBucket.getSockets());

        if(nonNull(firstBucket.getMeasurementType())) {
            this.setHardwareMeasurementType(firstBucket.getMeasurementType().toString());
        }
        if(nonNull(host.getHardwareType())){
            this.setHardwareType(host.getHardwareType().toString());
        }

    }
}

