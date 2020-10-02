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
package org.candlepin.subscriptions.pinhead;

import org.candlepin.subscriptions.inventory.client.InventoryServiceProperties;
import org.candlepin.subscriptions.pinhead.client.ApiException;
import org.candlepin.subscriptions.pinhead.client.PinheadApiProperties;
import org.candlepin.subscriptions.pinhead.client.model.OrgInventory;
import org.candlepin.subscriptions.pinhead.client.resources.PinheadApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.validation.constraints.Pattern;

/**
 * Abstraction around pulling data from pinhead.
 */
@Service
@Validated
public class PinheadService {

    private static final Logger log = LoggerFactory.getLogger(PinheadService.class);
    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ssX";

    private final PinheadApi api;
    private final int batchSize;
    private final RetryTemplate retryTemplate;
    private final Duration hostCheckinThreshold;

    @Autowired
    public PinheadService(InventoryServiceProperties inventoryServiceProperties,
        PinheadApiProperties apiProperties, PinheadApi api,
        @Qualifier("pinheadRetryTemplate") RetryTemplate retryTemplate) {
        this.hostCheckinThreshold = inventoryServiceProperties.getHostLastSyncThreshold();
        this.batchSize = apiProperties.getRequestBatchSize();
        this.api = api;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Return a page of consumers for the given org using the offset provided and with consumers that have
     * checked in since the lastCheckinTime.  This function accepts the last check in time as a String
     * parameter in order to validate the format prior to sending the request over the wire.  The format is
     * similar to ISO 8601 format but the code on the other end is matching to the regular expression we're
     * using for validation rather than doing full ISO 8601 parsing.  Therefore, things like timezone
     * offsets are not acceptable for the request even though they are valid ISO 8601 formats.
     * @param orgId organization id
     * @param offset offset in the listing of results
     * @param lastCheckinTime in the form YYYY-MM-DDTHH-MM-SSZ (e.g. 2012-12-18T23:56:23Z).
     * @return an OrgInventory object with the relevant results
     */
    public OrgInventory getPageOfConsumers(String orgId, String offset,
        @Pattern(regexp = "^([12]\\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])T(0[0-9]|1[0-9]|2[1-4]):" +
        "(0[0-9]|[1-5][0-9]):(0[0-9]|[1-5][0-9]))Z$") String lastCheckinTime) throws ApiException {
        return retryTemplate.execute(context -> {
            log.debug("Fetching page of consumers for org {}.", orgId);
            OrgInventory consumersForOrg = api.getConsumersForOrg(orgId, batchSize, offset, lastCheckinTime);
            log.debug("Consumer fetch complete. Found {} for batch of {}.", consumersForOrg.getFeeds().size(),
                batchSize);
            return consumersForOrg;
        });
    }

    /**
     * Utility method to return the time in a format expected by getPageOfConsumers.  This design seems rather
     * backwards, but we do it this way because if we overrode getPageOfConsumers -- taking the orgId and
     * offset as parameters and determined the timestamp internally -- we wouldn't get the validation call
     * made on the lastCheckinTime since internal calls within a class are not subject to Spring's AOP method
     * interception due to the way Spring constructs AOP proxies.  See
     *
     * https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html
     * #aop-understanding-aop-proxies
     *
     * @return the time in the form YYYY-MM-DDTHH-MM-SSZ (e.g. 2012-12-18T23:56:23Z).
     */
    public String formattedTime() {
        DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN).withZone(ZoneId.of("UTC"));
        return formatter.format(Instant.now().minus(hostCheckinThreshold));
    }
}
