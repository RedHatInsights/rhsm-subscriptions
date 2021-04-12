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
package org.candlepin.subscriptions.marketplace;

import org.candlepin.subscriptions.http.HttpClientProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Properties for the Marketplace integration.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rhsm-subscriptions.marketplace")
public class MarketplaceProperties extends HttpClientProperties {

    /**
     * Marketplace API key (from https://marketplace.redhat.com/en-us/account/service-ids)
     */
    private String apiKey;

    /**
     * Amount of time prior to token expiration to request a new token anyways.
     */
    private Duration tokenRefreshPeriod = Duration.ofMinutes(1);

    /**
     * How many attempts before giving up.
     */
    private Integer maxAttempts;

    /**
     * Retry backoff interval.
     */
    private Duration backOffInitialInterval;

    /**
     * Retry backoff interval.
     */
    private Duration backOffMaxInterval;

    /**
     * Retry exponential backoff multiplier.
     */
    private Double backOffMultiplier;

    /**
     * Verify that batches were accepted by Marketplace.
     */
    private boolean verifyBatches = true;

    private List<String> eligibleSwatchProductIds = new ArrayList<>();

    /**
     * Allows manually submitting marketplace tally summary.
     */
    private boolean isManualMarketplaceSubmissionEnabled;

}
