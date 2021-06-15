package org.candlepin.subscriptions.capacity;

import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CapacityReconciliationController {

    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationClock clock;
    private final OfferingRepository offeringRepository;
    private final ProductWhitelist productWhitelist;
    private final CapacityProductExtractor productExtractor;

    public CapacityReconciliationController(
            SubscriptionRepository subscriptionRepository,
            OfferingRepository offeringRepository,
            ProductWhitelist productWhitelist,
            CapacityProductExtractor productExtractor,
            ApplicationClock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.offeringRepository = offeringRepository;
        this.productWhitelist = productWhitelist;
        this.productExtractor = productExtractor;
        this.clock = clock;
    }

    public void reconcileCapacityForSubscription(Subscription subscription){
        Collection<SubscriptionCapacity>  subscriptionCapacities = mapSubscriptionToCapacities(subscription);
        reconcileSubscriptionCapacities(subscriptionCapacities);
    }

    private Collection<SubscriptionCapacity> mapSubscriptionToCapacities(Subscription subscription){

        if(productWhitelist.productIdMatches(subscription.getSku())){

            List<String> productIds = extractProductIdsForProduct(subscription.getSku());
            String derivedSku = getDerivedSku(subscription.getSku());
            List<String> derivedProductIds = extractProductIdsForProduct(derivedSku);

            Set<String> products = productExtractor.getProducts(productIds);
            Set<String> derivedProducts = productExtractor.getProducts(derivedProductIds);

            HashSet<String> allProducts = new HashSet<>(products);
            allProducts.addAll(derivedProducts);

            return allProducts.stream()
                    .map(
                            product -> SubscriptionCapacity.builder()
                                    .key(SubscriptionCapacityKey.builder()
                                            .subscriptionId(subscription.getSubscriptionId())
                                            .ownerId(subscription.getOwnerId())
                                            .productId(product)
                                            .build())
                                    .accountNumber(subscription.getAccountNumber())
                                    .build())
                    .collect(Collectors.toList());
        }else{
            //TODO: what if the sku is not on the whitelist
            return null;
        }

    }

    private String getDerivedSku(String sku) {
        return null;
    }

    private List<String> extractProductIdsForProduct(String sku) {
        return new ArrayList<>();
    }

    private void reconcileSubscriptionCapacities(Collection<SubscriptionCapacity> newState){

    }

    public void reconcileCapacityForSubscriptionId(String subscriptionId) {
        final Optional<org.candlepin.subscriptions.db.model.Subscription> maybePresent = subscriptionRepository.findActiveSubscription(subscriptionId);
        maybePresent.ifPresent(this::reconcileCapacityForSubscription);
        //TODO: What if subscription does not exist for id.
    }
}
