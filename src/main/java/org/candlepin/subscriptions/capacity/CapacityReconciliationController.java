package org.candlepin.subscriptions.capacity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.candlepin.subscriptions.db.model.SubscriptionCapacity.from;

@Component
@Slf4j
public class CapacityReconciliationController {

    private final OfferingRepository offeringRepository;
    private final ProductWhitelist productWhitelist;
    private final CapacityProductExtractor productExtractor;
    private final SubscriptionCapacityRepository subscriptionCapacityRepository;
    private final Counter capacityRecordsCreated;
    private final Counter capacityRecordsUpdated;
    private final Counter capacityRecordsDeleted;

    public CapacityReconciliationController(
            OfferingRepository offeringRepository,
            ProductWhitelist productWhitelist,
            CapacityProductExtractor productExtractor,
            SubscriptionCapacityRepository subscriptionCapacityRepository,
            MeterRegistry meterRegistry) {
        this.offeringRepository = offeringRepository;
        this.productWhitelist = productWhitelist;
        this.productExtractor = productExtractor;
        this.subscriptionCapacityRepository = subscriptionCapacityRepository;
        capacityRecordsCreated = meterRegistry.counter("rhsm-subscriptions.capacity.records_created");
        capacityRecordsUpdated = meterRegistry.counter("rhsm-subscriptions.capacity.records_updated");
        capacityRecordsDeleted = meterRegistry.counter("rhsm-subscriptions.capacity.records_deleted");
    }

    public void reconcileCapacityForSubscription(Subscription subscription){

        Collection<SubscriptionCapacity> newCapacities = mapSubscriptionToCapacities(subscription);
        reconcileSubscriptionCapacities(newCapacities, subscription.getSubscriptionId(), subscription.getSku());
    }

    private Collection<SubscriptionCapacity> mapSubscriptionToCapacities(Subscription subscription){

        Offering offering = offeringRepository.getById(subscription.getSku());
        Set<String> products = productExtractor
                    .getProducts(offering.getProductIds().stream().map(Object::toString).collect(Collectors.toSet()));

        return products.stream()
                    .map(product -> from(subscription,offering, product))
                    .collect(Collectors.toList());
    }

    private void reconcileSubscriptionCapacities(Collection<SubscriptionCapacity> newCapacities, String subscriptionId, String sku){

        Collection<SubscriptionCapacity> toSave = new ArrayList<>();
        Map<SubscriptionCapacityKey, SubscriptionCapacity> existingCapacityMap = subscriptionCapacityRepository
                .findByKeySubscriptionId(subscriptionId)
                .stream()
                .collect(Collectors.toMap(SubscriptionCapacity::getKey, Function.identity()));

        if (productWhitelist.productIdMatches(sku)) {
            newCapacities.forEach(
                    newCapacity -> {
                        toSave.add(newCapacity);
                        SubscriptionCapacity oldVersion = existingCapacityMap.remove(newCapacity.getKey());
                        if (oldVersion != null) {
                            capacityRecordsUpdated.increment();
                        } else {
                            capacityRecordsCreated.increment();
                        } });
            subscriptionCapacityRepository.saveAll(toSave);
        }

        Collection<SubscriptionCapacity> toDelete = new ArrayList<>(existingCapacityMap.values());
        subscriptionCapacityRepository.deleteAll(toDelete);
        if (!toDelete.isEmpty()) {
            log.info("Update for subscription ID {} removed {} incorrect capacity records.", subscriptionId, toDelete.size());
        }
        capacityRecordsDeleted.increment(toDelete.size());
    }
}
