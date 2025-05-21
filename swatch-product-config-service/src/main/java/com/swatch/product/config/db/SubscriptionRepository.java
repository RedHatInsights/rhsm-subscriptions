package com.swatch.product.config.db;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SubscriptionRepository implements PanacheRepositoryBase<SubscriptionDefinition, String> {}
