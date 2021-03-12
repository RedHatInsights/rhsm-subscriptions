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
import org.candlepin.subscriptions.files.ProductProfile;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;

/**
 * Collects instances and tallies based on hourly metrics.
 */
public class MetricUsageCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricUsageCollector.class);

    private final AccountRepository accountRepository;
    private final EventController eventController;
    private final ApplicationClock clock;
    private final ProductProfile productProfile;

    public MetricUsageCollector(ProductProfile productProfile, AccountRepository accountRepository,
        EventController eventController, ApplicationClock clock) {
        this.accountRepository = accountRepository;
        this.eventController = eventController;
        this.clock = clock;
        this.productProfile = productProfile;
    }

    @Transactional
    public AccountUsageCalculation collect(String accountNumber, OffsetDateTime startDateTime,
        OffsetDateTime endDateTime) {

        Account account = accountRepository.findById(accountNumber).orElseThrow(() ->
            new SubscriptionsException(ErrorCode.OPT_IN_REQUIRED, Response.Status.BAD_REQUEST,
            "Account not found!",
            String.format("Account %s was not found. Account not opted in?", accountNumber))
        );
        Map<String, Host> serviceInstances = new HashMap<>();
        OffsetDateTime newestInstanceTimestamp = OffsetDateTime.MIN;
        for (Host host : account.getServiceInstances().values()) {
            if (productProfile.getServiceType().equals(host.getInstanceType())) {
                serviceInstances.put(host.getInstanceId(), host);
                newestInstanceTimestamp = newestInstanceTimestamp.isAfter(host.getLastSeen()) ?
                    newestInstanceTimestamp : host.getLastSeen();
            }
        }
        OffsetDateTime effectiveStartDateTime;
        OffsetDateTime effectiveEndDateTime;
        boolean recalculatingMonthlyTotals;
        if (newestInstanceTimestamp.isAfter(startDateTime)) {
            effectiveStartDateTime = clock.startOfMonth(startDateTime);
            effectiveEndDateTime = clock.endOfMonth(endDateTime);
            recalculatingMonthlyTotals = true;
        }
        else {
            effectiveStartDateTime = startDateTime;
            effectiveEndDateTime = endDateTime;
            recalculatingMonthlyTotals = false;
        }

        if (recalculatingMonthlyTotals) {
            log.info("Clearing monthly totals for {} instances", serviceInstances.size());
            serviceInstances.values().forEach(instance -> instance.clearMonthlyTotals(effectiveStartDateTime,
                effectiveEndDateTime));
        }

        Stream<Event> eventStream = eventController.fetchEventsInTimeRange(accountNumber,
            effectiveStartDateTime,
            effectiveEndDateTime)
            .filter(event -> event.getServiceType().equals(productProfile.getServiceType()));

        eventStream.forEach(event -> {
            String instanceId = event.getInstanceId();
            Host existing = serviceInstances.get(instanceId);
            Host host = existing == null ? new Host() : existing;
            updateInstanceFromEvent(event, host);
            serviceInstances.put(instanceId, host);
            account.getServiceInstances().put(instanceId, host);
        });

        accountRepository.save(account);
        return tallyCurrentAccountState(account);
    }

    private AccountUsageCalculation tallyCurrentAccountState(Account account) {
        AccountUsageCalculation accountUsageCalculation = new AccountUsageCalculation(
            account.getAccountNumber());
        account.getServiceInstances().values().forEach(instance -> instance.getBuckets().forEach(bucket -> {
            UsageCalculation.Key usageKey = new UsageCalculation.Key(bucket.getKey().getProductId(),
                bucket.getKey().getSla(), bucket.getKey().getUsage());
            instance.getMeasurements().forEach((uom, value) -> accountUsageCalculation
                .addUsage(usageKey, getHardwareMeasurementType(instance), uom, value));
        }));
        return accountUsageCalculation;
    }

    private void updateInstanceFromEvent(Event event, Host instance) {
        instance.setAccountNumber(event.getAccountNumber());
        instance.setInstanceType(event.getServiceType());
        instance.setInstanceId(event.getInstanceId());
        Optional.ofNullable(event.getCloudProvider())
            .map(this::getCloudProvider)
            .map(HardwareMeasurementType::toString)
            .ifPresent(instance::setCloudProvider);
        Optional.ofNullable(event.getHardwareType())
            .map(this::getHostHardwareType)
            .ifPresent(instance::setHardwareType);
        instance.setDisplayName(Optional.ofNullable(event.getDisplayName())
            .map(Optional::get)
            .orElse(event.getInstanceId()));
        instance.setLastSeen(event.getTimestamp());
        instance.setGuest(instance.getHardwareType() == HostHardwareType.VIRTUALIZED);
        Optional.ofNullable(event.getInventoryId())
            .map(Optional::get)
            .ifPresent(instance::setInventoryId);
        Optional.ofNullable(event.getHypervisorUuid())
            .map(Optional::get)
            .ifPresent(instance::setHypervisorUuid);
        Optional.ofNullable(event.getSubscriptionManagerId())
            .map(Optional::get)
            .ifPresent(instance::setSubscriptionManagerId);
        Optional.ofNullable(event.getMeasurements())
            .orElse(Collections.emptyList())
            .forEach(measurement -> {
                instance.setMeasurement(measurement.getUom(), measurement.getValue());
                instance.addToMonthlyTotal(event.getTimestamp(), measurement.getUom(),
                    measurement.getValue());
            });
        addBucketsFromEvent(instance, event);
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

    private HardwareMeasurementType getHardwareMeasurementType(Host instance) {
        if (instance.getHardwareType() == null) {
            return HardwareMeasurementType.PHYSICAL;
        }
        switch (instance.getHardwareType()) {
            case CLOUD:
                return getCloudProvider(instance);
            case VIRTUALIZED:
                return HardwareMeasurementType.VIRTUAL;
            case PHYSICAL:
                return HardwareMeasurementType.PHYSICAL;
            default:
                throw new IllegalArgumentException(String.format("Unsupported hardware type: %s",
                    instance.getHardwareType()));
        }
    }

    private HardwareMeasurementType getCloudProvider(Host instance) {
        if (instance.getCloudProvider() == null) {
            throw new IllegalArgumentException("Hardware type cloud, but no cloud provider specified");
        }
        return HardwareMeasurementType.valueOf(instance.getCloudProvider());
    }

    private HardwareMeasurementType getCloudProvider(Event.CloudProvider cloudProvider) {
        switch (cloudProvider) {
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
                    cloudProvider.value()));
        }
    }

    private void addBucketsFromEvent(Host host, Event event) {
        ServiceLevel effectiveSla = Optional.ofNullable(event.getSla())
            .map(Event.Sla::toString)
            .map(ServiceLevel::fromString)
            .orElse(productProfile.getDefaultSla());
        Usage effectiveUsage = Optional.ofNullable(event.getUsage())
            .map(Event.Usage::toString)
            .map(Usage::fromString)
            .orElse(productProfile.getDefaultUsage());
        Set<String> productIds = getProductIds(event);

        for (String productId : productIds) {
            for (ServiceLevel sla : Set.of(effectiveSla, ServiceLevel._ANY)) {
                for (Usage usage : Set.of(effectiveUsage, Usage._ANY)) {
                    HostTallyBucket bucket = new HostTallyBucket();
                    bucket.setKey(new HostBucketKey(host, productId, sla, usage, false));
                    host.addBucket(bucket);
                }
            }
        }
    }

    private Set<String> getProductIds(Event event) {
        Set<String> productIds = new HashSet<>();
        Stream.of(event.getRole())
            .filter(Objects::nonNull)
            .map(role -> productProfile.getSwatchProductsByRoles().getOrDefault(role.value(),
                Collections.emptySet()))
            .forEach(productIds::addAll);

        Optional.ofNullable(event.getProductIds()).orElse(Collections.emptyList()).stream()
            .map(productProfile.getSwatchProductsByEngProducts()::get)
            .filter(Objects::nonNull)
            .forEach(productIds::addAll);

        return productIds;
    }

}
