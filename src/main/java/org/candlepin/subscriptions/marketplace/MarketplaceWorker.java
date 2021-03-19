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

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.json.TallySummary;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import io.micrometer.core.annotation.Timed;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

/**
 * Worker that maps tally summaries and submits them to Marketplace.
 */
@Service
public class MarketplaceWorker {

    @Getter
    @Setter
    private String topic;

    private final MarketplaceProducer producer;
    private final MarketplacePayloadMapper marketplacePayloadMapper;

    public MarketplaceWorker(ApplicationProperties properties, MarketplaceProducer producer,
        MarketplacePayloadMapper marketplacePayloadMapper) {
        topic = properties.getTallySummaryTopic();
        this.producer = producer;
        this.marketplacePayloadMapper = marketplacePayloadMapper;
    }

    @Timed("rhsm-subscriptions.marketplace.tally-summary")
    @KafkaListener(id = "marketplace-worker", topics = "#{__listener.topic}",
        containerFactory = "kafkaTallySummaryListenerContainerFactory")
    public void receive(TallySummary tallySummary) {
        Optional.ofNullable(marketplacePayloadMapper.createUsageRequest(tallySummary))
            .filter(s -> !s.getData().isEmpty())
            .ifPresent(producer::submitUsageRequest);
    }
}
