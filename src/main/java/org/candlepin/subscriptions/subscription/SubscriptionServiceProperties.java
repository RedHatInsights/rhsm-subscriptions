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
package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.http.HttpClientProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;

/**
 * Additional properties related to the Subscription Service
 */
@Getter
@Setter
@ToString
public class SubscriptionServiceProperties extends HttpClientProperties {

    /**
     * Number of times we should try requesting info from the Subscription Service if something fails.
     */
    private int maxRetryAttempts = 4;

    /** Page size for subscription queries */
    private int pageSize = 500;

    /**
     * The initial sleep interval between retries when retrying fetching info from the Subscription Service
     */
    private Duration backOffInitialInterval = Duration.ofSeconds(1L);
}
