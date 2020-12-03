/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.annotation.Timed;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides the logic for updating Tally snapshots.
 */
@Component
public class TallySnapshotController {

    private static final Logger log = LoggerFactory.getLogger(TallySnapshotController.class);

    private final ApplicationProperties props;
    private final InventoryAccountUsageCollector usageCollector;
    private final CloudigradeAccountUsageCollector cloudigradeCollector;
    private final UsageSnapshotProducer snapshotProducer;
    private final RetryTemplate retryTemplate;
    private final RetryTemplate cloudigradeRetryTemplate;

    private final Set<String> applicableProducts;

    public TallySnapshotController(
        ApplicationProperties props,
        @Qualifier("applicableProducts") Set<String> applicableProducts,
        InventoryAccountUsageCollector usageCollector,
        CloudigradeAccountUsageCollector cloudigradeCollector,
        UsageSnapshotProducer snapshotProducer,
        @Qualifier("collectorRetryTemplate") RetryTemplate retryTemplate,
        @Qualifier("cloudigradeRetryTemplate") RetryTemplate cloudigradeRetryTemplate) {

        this.props = props;
        this.applicableProducts = applicableProducts;
        this.usageCollector = usageCollector;
        this.cloudigradeCollector = cloudigradeCollector;
        this.snapshotProducer = snapshotProducer;
        this.retryTemplate = retryTemplate;
        this.cloudigradeRetryTemplate = cloudigradeRetryTemplate;
    }

    @Timed("rhsm-subscriptions.snapshots.single")
    public void produceSnapshotsForAccount(String account) {
        produceSnapshotsForAccounts(Collections.singletonList(account));
    }

    @Timed("rhsm-subscriptions.snapshots.collection")
    public void produceSnapshotsForAccounts(List<String> accounts) {
        log.info("Producing snapshots for {} accounts.", accounts.size());
        // Account list could be large. Only print them when debugging.
        if (log.isDebugEnabled()) {
            log.debug("Producing snapshots for accounts: {}", String.join(",", accounts));
        }

        Map<String, AccountUsageCalculation> accountCalcs;
        try {
            accountCalcs = retryTemplate.execute(context ->
                usageCollector.collect(this.applicableProducts, accounts)
            );
            if (props.isCloudigradeEnabled()) {
                attemptCloudigradeEnrichment(accounts, accountCalcs);
            }
        }
        catch (Exception e) {
            log.error("Could not collect existing usage snapshots for accounts {}", accounts, e);
            return;
        }

        snapshotProducer.produceSnapshotsFromCalculations(accounts, accountCalcs.values());
    }

    private void attemptCloudigradeEnrichment(List<String> accounts,
        Map<String, AccountUsageCalculation> accountCalcs) {
        log.info("Adding cloudigrade reports to calculations.");
        try {
            cloudigradeRetryTemplate.execute(context -> {
                try {
                    cloudigradeCollector.enrichUsageWithCloudigradeData(accountCalcs, accounts);
                }
                catch (Exception e) {
                    throw new ExternalServiceException(
                        ErrorCode.REQUEST_PROCESSING_ERROR,
                        "Error during attempt to integrate cloudigrade report",
                        e
                    );
                }
                return null; // RetryCallback requires a return
            });
        }
        catch (Exception e) {
            log.warn("Exception during cloudigrade enrichment, tally will not be enriched.", e);
        }
    }

}
