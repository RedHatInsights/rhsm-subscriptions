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
package org.candlepin.subscriptions.files;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Cache for a value that expires after a certain time.
 *
 * Uses of this class must check whether the cached value is valid by using isExpired.
 *
 * @param <T> type of value to cache.
 */
public class Cache<T> {
    private OffsetDateTime lastCached;
    private T cachedValue;
    private final Duration cacheTtl;
    private final Clock clock;

    public Cache(Clock clock, Duration cacheTtl) {
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    /**
     * Set the value in the cache and transparently update the TTL on the value.
     *
     * @param value value to cache
     */
    public void setValue(T value) {
        cachedValue = value;
        lastCached = OffsetDateTime.now(clock);
    }

    /**
     * Get the cached value.
     *
     * @return the cached value
     */
    public T getValue() {
        return cachedValue;
    }

    /**
     * Returns whether the TTL has elapsed or not.
     *
     * @return boolean indicating TTL expiry
     */
    public boolean isExpired() {
        if (lastCached == null) {
            return true;
        }
        OffsetDateTime expiry = lastCached.plus(cacheTtl);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return expiry.isBefore(now) || expiry.isEqual(now);
    }
}
