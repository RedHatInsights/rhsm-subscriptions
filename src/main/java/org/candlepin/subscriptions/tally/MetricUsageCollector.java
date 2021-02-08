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
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.json.Event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;

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
        public static final String OPENSHIFT_PRODUCT_ID = "OpenShift Container Platform (metrics)";
        public static final String SERVICE_TYPE = "OPENSHIFT_CLUSTER";
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

        public Map<Event.Role, List<String>> getRoleProductIdMapping() {
            return Collections.emptyMap();
        }

        public Map<String, List<String>> getEngineeringProductIdToSwatchProductMapping() {
            return Collections.emptyMap();
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

        Account account = accountRepository.findById(accountNumber).orElseThrow(() ->
            new SubscriptionsException(ErrorCode.OPT_IN_REQUIRED, Response.Status.BAD_REQUEST,
            "Account not found!",
            String.format("Account %s was not found. Account not opted in?", accountNumber))
        );
        Map<String, Host> existingInstances = account.getServiceInstances().values().stream()
            .filter(host -> productConfig.getServiceType().equals(host.getInstanceType()))
            .collect(Collectors.toMap(Host::getInstanceId, Function.identity()));
        AccountUsageCalculation accountUsageCalculation = new AccountUsageCalculation(accountNumber);
        Stream<Event> eventStream = eventController.fetchEventsInTimeRange(accountNumber, begin, end)
            .filter(event -> event.getServiceType().equals(productConfig.getServiceType()));

        eventStream.forEach(event -> {
            String instanceId = event.getInstanceId();
            Host existing = existingInstances.remove(instanceId);
            Host host = existing == null ? new Host() : existing;
            host.setAccountNumber(accountNumber);
            host.setInstanceType(event.getServiceType());
            host.setInstanceId(instanceId);
            Optional.ofNullable(event.getCloudProvider())
                .map(Event.CloudProvider::toString)
                .ifPresent(host::setCloudProvider);
            Optional.ofNullable(event.getHardwareType())
                .map(this::getHostHardwareType)
                .ifPresent(host::setHardwareType);
            host.setDisplayName(Optional.ofNullable(event.getDisplayName())
                .map(Optional::get)
                .orElse(event.getInstanceId()));
            host.setLastSeen(event.getTimestamp());
            host.setGuest(host.getHardwareType() == HostHardwareType.VIRTUALIZED);
            Optional.ofNullable(event.getInventoryId())
                .map(Optional::get)
                .ifPresent(host::setInventoryId);
            Optional.ofNullable(event.getHypervisorUuid())
                .map(Optional::get)
                .ifPresent(host::setHypervisorUuid);
            Optional.ofNullable(event.getSubscriptionManagerId())
                .map(Optional::get)
                .ifPresent(host::setSubscriptionManagerId);
            HardwareMeasurementType hardwareMeasurementType = getHardwareMeasurementType(event);
            Set<UsageCalculation.Key> usageKeys = getApplicableFilterCombinations(event);
            Optional.ofNullable(event.getMeasurements())
                .orElse(Collections.emptyList()).forEach(measurement -> {
                    host.setMeasurement(measurement.getUom(), measurement.getValue());
                    usageKeys.forEach(key ->
                        accountUsageCalculation
                        .addUsage(key, hardwareMeasurementType, measurement.getUom(), measurement.getValue())
                    );
                });
            addBuckets(host, usageKeys);
            account.getServiceInstances().put(instanceId, host);
        });

        accountRepository.save(account);

        log.info("Removing {} stale {} records (metrics no longer present).",
            existingInstances.size(), productConfig.getServiceType());
        existingInstances.keySet().forEach(account.getServiceInstances()::remove);

        accountRepository.save(account);
        return accountUsageCalculation;
    }

    private HostHardwareType getHostHardwareType(Event.HardwareType hardwareType) {
        switch (hardwareType) {
            case __EMPTY__:
                return null;
            case PHYSICAL:
                return HostHardwareType.PHYSICAL;
            case VIRTUAL:
                return HostHardwareType.VIRTUALIZED;
            case CLOUD:
                return HostHardwareType.CLOUD;
            default:
                throw new IllegalArgumentException(String.format("Unsupported hardware type: %s",
                    hardwareType));
        }
    }

    private HardwareMeasurementType getHardwareMeasurementType(Event event) {
        if (event.getHardwareType() == null) {
            return HardwareMeasurementType.PHYSICAL;
        }
        switch (event.getHardwareType()) {
            case __EMPTY__:
                return null;
            case CLOUD:
                return getCloudProvider(event);
            case VIRTUAL:
                return HardwareMeasurementType.VIRTUAL;
            case PHYSICAL:
                return HardwareMeasurementType.PHYSICAL;
            default:
                throw new IllegalArgumentException(String.format("Unsupported hardware type: %s",
                    event.getHardwareType()));
        }
    }

    private HardwareMeasurementType getCloudProvider(Event event) {
        if (event.getHardwareType() == Event.HardwareType.CLOUD && event.getCloudProvider() == null) {
            throw new IllegalArgumentException("Hardware type cloud, but no cloud provider specified");
        }
        switch (event.getCloudProvider()) {
            case __EMPTY__:
                return null;
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
            bucket.setKey(new HostBucketKey(host, key.getProductId(), key.getSla(), key.getUsage(), false));
            host.addBucket(bucket);
        }
    }

    private Set<UsageCalculation.Key> getApplicableFilterCombinations(Event event) {
        ServiceLevel effectiveSla = Optional.ofNullable(event.getSla())
            .map(Event.Sla::toString)
            .map(ServiceLevel::fromString)
            .orElse(productConfig.getDefaultSla());
        Usage effectiveUsage = Optional.ofNullable(event.getUsage())
            .map(Event.Usage::toString)
            .map(Usage::fromString)
            .orElse(productConfig.getDefaultUsage());
        Set<String> productIds = getProductIds(event);

        Set<UsageCalculation.Key> keys = new HashSet<>();
        for (String productId : productIds) {
            for (ServiceLevel sla : Set.of(effectiveSla, ServiceLevel._ANY)) {
                for (Usage usage : Set.of(effectiveUsage, Usage._ANY)) {
                    keys.add(new UsageCalculation.Key(productId, sla, usage));
                }
            }
        }
        return keys;
    }

    private Set<String> getProductIds(Event event) {
        Set<String> productIds = new HashSet<>();
        productIds.add(productConfig.getProductId()); // this product ID always applies
        Stream.of(event.getRole())
            .filter(Objects::nonNull)
            .map(role -> productConfig.getRoleProductIdMapping().getOrDefault(role, Collections.emptyList()))
            .forEach(productIds::addAll);
        Optional.ofNullable(event.getProductIds()).orElse(Collections.emptyList()).stream()
            .map(productConfig.getEngineeringProductIdToSwatchProductMapping()::get)
            .filter(Objects::nonNull)
            .forEach(productIds::addAll);
        return productIds;
    }

}
