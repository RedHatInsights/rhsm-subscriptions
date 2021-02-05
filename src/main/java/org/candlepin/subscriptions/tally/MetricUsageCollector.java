/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.db.AccountRepository;
import org.candlepin.subscriptions.db.model.Account;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collects instances and tallies based on hourly metrics.
 */
@Component
public class MetricUsageCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricUsageCollector.class);

    private final AccountRepository accountRepository;
    private final EventController eventController;
    private final ProductConfig productConfig;

    /**
     * Encapsulates all per-product information we anticipate putting into configuration used by this
     * collector.
     */
    public static class ProductConfig {
        private static final String OPENSHIFT_PRODUCT_ID = "OpenShift Container Platform (hourly)";
        private static final String SERVICE_TYPE = "OPENSHIFT_CLUSTER";
        private static final ServiceLevel DEFAULT_SLA = ServiceLevel.PREMIUM;
        private static final Usage DEFAULT_USAGE = Usage.PRODUCTION;

        public String getServiceType() {
            return SERVICE_TYPE;
        }

        public String getProductId() {
            return OPENSHIFT_PRODUCT_ID;
        }

        public Usage getDefaultUsage() {
            return DEFAULT_USAGE;
        }

        public ServiceLevel getDefaultSla() {
            return DEFAULT_SLA;
        }
    }

    public MetricUsageCollector(AccountRepository accountRepository,
        EventController eventController) {

        this.accountRepository = accountRepository;
        this.eventController = eventController;
        this.productConfig = new ProductConfig();
    }

    @Transactional
    public AccountUsageCalculation collect(String accountNumber, OffsetDateTime begin,
        OffsetDateTime end) {

        Account account = accountRepository.getOne(accountNumber);
        Map<UUID, Host> existingInstances = account.getHosts().values().stream()
            .filter(host -> host.getType().equals(productConfig.getServiceType()))
            .collect(Collectors.toMap(Host::getId, Function.identity()));
        AccountUsageCalculation accountUsageCalculation = new AccountUsageCalculation(accountNumber);
        Stream<Event> eventStream = eventController.fetchEventsInTimeRange(accountNumber, begin, end)
            .filter(event -> event.getServiceType().equals(productConfig.getServiceType()));

        eventStream.forEach(event -> {
            UUID instanceId = UUID.fromString(event.getInstanceId());

            Host existing = existingInstances.remove(instanceId);
            Host host = existing == null ? new Host(instanceId) : existing;
            host.setAccountNumber(accountNumber);
            UsageCalculation.Key primaryUsageKey = getPrimaryUsageKey(event);
            Set<UsageCalculation.Key> usageKeys = getApplicableFilterCombinations(event);
            HardwareMeasurementType category = getCategory(event);
            event.getMeasurements().forEach(measurement -> {
                host.setMeasurement(measurement.getUom(), measurement.getValue());
                accountUsageCalculation.addBilledUsage(primaryUsageKey, category, measurement.getUom(),
                    measurement.getValue());
            });
            host.setDisplayName(event.getDisplayName().orElse(event.getInstanceId()));
            addBuckets(host, usageKeys);

            //TODO
            int cores = 0;

            usageKeys.stream().map(accountUsageCalculation::getOrCreateCalculation).forEach(usageCalculation -> {
                usageCalculation.addPhysical(cores, 0, 1);
            });
            account.getHosts().put(instanceId, host);
        });

        accountRepository.save(account);

        log.info("Removing {} stale {} records (metrics no longer present).",
            productConfig.getServiceType(), existingInstances.size());
        existingInstances.keySet().forEach(account.getHosts()::remove);

        accountRepository.save(account);
        return accountUsageCalculation;
    }

    private HardwareMeasurementType getCategory(Event event) {

        switch (event.getHardwareType()) {
        case CLOUD:
            return getCloudProvider(event);
        case VIRTUAL:
            return HardwareMeasurementType.VIRTUAL;
        case PHYSICAL:
        default:
            return HardwareMeasurementType.PHYSICAL;
        }
    }

    private HardwareMeasurementType getCloudProvider(Event event) {
        switch (event.getCloudProvider()) {
        case AWS:
            return HardwareMeasurementType.AWS;
        case AZURE:
            return HardwareMeasurementType.AZURE;
        case ALIBABA:
            return HardwareMeasurementType.ALIBABA;
        case GOOGLE:
            return HardwareMeasurementType.GOOGLE;
        default:
            throw new IllegalArgumentException(String.format("Unsupported value for cloud provider: %s",
                event.getCloudProvider().value()));
        }
    }

    private void addBuckets(Host host, Set<UsageCalculation.Key> usageKeys) {
        for (UsageCalculation.Key key : usageKeys) {
            HostTallyBucket bucket = new HostTallyBucket();
            bucket.setMeasurementType(HardwareMeasurementType.TOTAL);
            bucket.setKey(new HostBucketKey(host, key.getProductId(), key.getSla(), key.getUsage(), false));
            host.addBucket(bucket);
        }
    }

    private UsageCalculation.Key getPrimaryUsageKey(Event event) {
        ServiceLevel effectiveSla = Optional.ofNullable(event.getSla())
            .map(Event.Sla::toString)
            .map(ServiceLevel::fromString)
            .orElse(productConfig.getDefaultSla());
        Usage effectiveUsage = Optional.ofNullable(event.getUsage())
            .map(Event.Usage::toString)
            .map(Usage::fromString)
            .orElse(productConfig.getDefaultUsage());
        return new UsageCalculation.Key(productConfig.getProductId(), effectiveSla, effectiveUsage);
    }

    private Set<UsageCalculation.Key> getApplicableFilterCombinations(Event event) {
        UsageCalculation.Key primaryUsageKey = getPrimaryUsageKey(event);
        List<String> productIds = Collections.singletonList(productConfig.getProductId());

        Set<UsageCalculation.Key> keys = new HashSet<>();
        for (String productId : productIds) {
            for (ServiceLevel sla : Arrays.asList(ServiceLevel._ANY, primaryUsageKey.getSla())) {
                for (Usage usage : Arrays.asList(Usage._ANY, primaryUsageKey.getUsage())) {
                    keys.add(new UsageCalculation.Key(productId, sla, usage));
                }
            }
        }
        return keys;
    }

}
