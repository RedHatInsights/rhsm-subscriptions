package com.swatch.product.config;

import com.swatch.product.config.db.ProductRepository;
import com.swatch.product.config.db.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ProductService {

  SubscriptionRepository repository;
  ProductRepository productRepository;

  @Inject
  public ProductService(SubscriptionRepository repository, ProductRepository productRepository) {
    this.repository = repository;
    this.productRepository = productRepository;
  }

  public List<String> findMatchingProductIds(ProductTagLookupParams params) {

    //    return repository.findAll().list().stream().map(Subscription::toString).toList();
    return productRepository.findAll().list().stream().map(p -> p.getId()).toList();
  }
}
