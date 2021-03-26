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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.marketplace.api.model.StatusResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jmx.JmxException;


@ExtendWith(MockitoExtension.class)
class MarketplaceJmxBeanTest {

    @Mock MarketplaceService service;
    @Mock MarketplaceProducer producer;
    @Mock ObjectMapper objMapper;
    @Mock MarketplacePayloadMapper payloadMapper;

    @Test
    void testSubmitTallySummaryEnablement() throws Exception {

        when(producer.submitUsageRequest(any())).thenReturn(new StatusResponse());

        ApplicationProperties appProps = new ApplicationProperties();
        MarketplaceProperties mktProps = new MarketplaceProperties();
        MarketplaceJmxBean bean = new MarketplaceJmxBean(appProps, mktProps, service, producer, objMapper,
            payloadMapper);

        assertTrue(!appProps.isDevMode() && !mktProps.isManualMarketplaceSubmissionEnabled());
        assertThrows(JmxException.class, () -> bean.submitTallySummary(""));

        mktProps.setManualMarketplaceSubmissionEnabled(true);
        assertTrue(!appProps.isDevMode() && mktProps.isManualMarketplaceSubmissionEnabled());
        bean.submitTallySummary("");

        appProps.setDevMode(true);
        mktProps.setManualMarketplaceSubmissionEnabled(false);
        assertTrue(appProps.isDevMode() && !mktProps.isManualMarketplaceSubmissionEnabled());
        mktProps.setManualMarketplaceSubmissionEnabled(false);
        bean.submitTallySummary("");
    }
}
