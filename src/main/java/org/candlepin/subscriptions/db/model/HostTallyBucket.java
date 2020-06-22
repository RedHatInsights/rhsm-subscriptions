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
package org.candlepin.subscriptions.db.model;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;


/**
 * Represents a bucket that this host contributes to.
 */
@Entity
@Table(name = "host_tally_buckets")
public class HostTallyBucket implements Serializable {

    @EmbeddedId
    private HostBucketKey key;

    public HostTallyBucket() {
    }

    public HostTallyBucket(Host host, String productId, ServiceLevel sla, Usage usage, Boolean asHypervisor) {
        setKey(new HostBucketKey(host, productId, sla, usage, asHypervisor));
    }

    public HostBucketKey getKey() {
        return key;
    }

    public void setKey(HostBucketKey key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof HostTallyBucket)) {
            return false;
        }

        HostTallyBucket that = (HostTallyBucket) o;
        return getKey().equals(that.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }

}
