package org.candlepin.subscriptions.capacity;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProductAttribute;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CapacityReconciliationController {

    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationClock clock;
    private final OfferingRepository offeringRepository;
    private final ProductWhitelist productWhitelist;
    private final CapacityProductExtractor productExtractor;
    private final SubscriptionCapacityRepository subscriptionCapacityRepository;

    public CapacityReconciliationController(
            SubscriptionRepository subscriptionRepository,
            OfferingRepository offeringRepository,
            ProductWhitelist productWhitelist,
            CapacityProductExtractor productExtractor,
            SubscriptionCapacityRepository subscriptionCapacityRepository,
            ApplicationClock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.offeringRepository = offeringRepository;
        this.productWhitelist = productWhitelist;
        this.productExtractor = productExtractor;
        this.subscriptionCapacityRepository = subscriptionCapacityRepository;
        this.clock = clock;
    }

    public void reconcileCapacityForSubscription(Subscription subscription){
        Collection<SubscriptionCapacity>  subscriptionCapacities = mapSubscriptionToCapacities(subscription);
        reconcileSubscriptionCapacities(subscriptionCapacities);
    }

    private Collection<SubscriptionCapacity> mapSubscriptionToCapacities(Subscription subscription){

        if(productWhitelist.productIdMatches(subscription.getSku())){

            Map<SubscriptionCapacityKey, SubscriptionCapacity> existingCapacityMap =
                    subscriptionCapacityRepository
                            .findByKeyOwnerIdAndKeySubscriptionIdIn(
                                    subscription.getOwnerId(),
                                    subscription.getSubscriptionId());


            Offering offering = offeringRepository.getById(subscription.getSku());
            List<Integer> productIds = offering.getProductIds();;
            String derivedSku = getDerivedSku(subscription.getSku()); //offering.getDerivedSku();
            //TODO: is derived sku always present? what to do if absent?
            List<Integer> derivedProductIds = offeringRepository.getById(derivedSku).getProductIds();

            Set<String> products = productExtractor.getProducts(productIds.stream().map(Object::toString).collect(Collectors.toSet()));
            Set<String> derivedProducts = productExtractor.getProducts(derivedProductIds.stream().map(Object::toString).collect(Collectors.toSet()));
            HashSet<String> allProducts = new HashSet<>(products);
            allProducts.addAll(derivedProducts);

            return allProducts.stream()
                    .map(
                            product -> {
                             SubscriptionCapacityKey key =   SubscriptionCapacityKey.builder()
                                     .subscriptionId(subscription.getSubscriptionId())
                                     .ownerId(subscription.getOwnerId())
                                     .productId(product)
                                     .build();

                             SubscriptionCapacity capacity =  SubscriptionCapacity.builder()
                                     .key(key)
                                     .accountNumber(subscription.getAccountNumber())
                                     .beginDate(subscription.getStartDate())
                                     .endDate(subscription.getEndDate())
                                     .serviceLevel(offering.getServiceLevel())
                                     .usage(offering.getUsage())
                                     .sku(offering.getSku())
                                     .physicalSockets(offering.getPhysicalSockets())
                                     .virtualSockets(offering.getVirtualSockets())
                                     .virtualCores(offering.getVirtualCores())
                                     .physicalCores(offering.getPhysicalCores())
                                     .build();

                             SubscriptionCapacity existingCapacity = existingCapacityMap.get(key);
                             capacity.setHasUnlimitedGuestSockets(existingCapacity.getHasUnlimitedGuestSockets());

                             return capacity;
                            })
                    .collect(Collectors.toList());
        }else{
            //TODO: what if the sku is not on the whitelist
            return null;
        }
    }


    private String getDerivedSku(String sku) {
        return "";
    }

    private void reconcileSubscriptionCapacities(Collection<SubscriptionCapacity> newState){

    }

    public void reconcileCapacityForSubscriptionId(String subscriptionId) {
        final Optional<org.candlepin.subscriptions.db.model.Subscription> maybePresent = subscriptionRepository.findActiveSubscription(subscriptionId);
        maybePresent.ifPresent(this::reconcileCapacityForSubscription);
        //TODO: What if subscription does not exist for id.
    }
}
