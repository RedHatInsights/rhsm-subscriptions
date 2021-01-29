/*
 * Copyright (c) 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.marketplace.api.model.AuthGrantType;
import org.candlepin.subscriptions.marketplace.api.model.AuthResponse;
import org.candlepin.subscriptions.marketplace.api.model.StatusResponse;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;
import org.candlepin.subscriptions.marketplace.api.resources.MarketplaceApi;
import org.candlepin.subscriptions.marketplace.auth.HttpBearerAuth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Encapsulates auth-related aspects of interacting with the Marketplace API.
 */
@Component
public class MarketplaceService {

    private final MarketplaceApi api;
    private final String apiKey;
    private final long tokenRefreshPeriodMs;
    private String accessToken;
    private long tokenRefreshCutoff;

    @Autowired
    public MarketplaceService(MarketplaceProperties properties, MarketplaceApi api) {
        this.api = api;
        this.apiKey = properties.getApiKey();
        this.tokenRefreshPeriodMs = properties.getTokenRefreshPeriod().toMillis() / 1000;
        this.accessToken = null;
        this.tokenRefreshCutoff = 0L;
    }

    public synchronized void forceRefreshAccessToken() throws ApiException {
        this.tokenRefreshCutoff = 0L;
        ensureAccessToken();
    }

    public synchronized void ensureAccessToken() throws ApiException {
        if (OffsetDateTime.now().toEpochSecond() > tokenRefreshCutoff) {
            AuthResponse response =
                api.getAccessToken(AuthGrantType.URN_IBM_PARAMS_OAUTH_GRANT_TYPE_APIKEY.getValue(), apiKey);

            accessToken = response.getAccessToken();
            tokenRefreshCutoff = response.getExpiration() - (tokenRefreshPeriodMs);
            HttpBearerAuth auth = (HttpBearerAuth) api.getApiClient().getAuthentication("accessToken");
            auth.setBearerToken(accessToken);
        }
    }

    public StatusResponse submitUsageEvents(UsageRequest usageRequest) throws ApiException {
        ensureAccessToken();
        return api.submitUsageEvents(usageRequest);
    }

    public StatusResponse getUsageBatchStatus(String batchId) throws ApiException {
        ensureAccessToken();
        return api.getUsageBatchStatus(batchId);
    }
}
