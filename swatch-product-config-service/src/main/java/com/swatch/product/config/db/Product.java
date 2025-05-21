package com.swatch.product.config.db;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.Set;

@ToString
@Entity
@Table(name = "product", schema = "swatch_product_config")
public class Product {
  @Id
  @Column(name = "id", nullable = false, length = Integer.MAX_VALUE)
  private String id;

  @ToString.Exclude
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private SubscriptionDefinition subscriptionDefinition;

  @Column(name = "level1", length = Integer.MAX_VALUE)
  private String level1;

  @Column(name = "level2", length = Integer.MAX_VALUE)
  private String level2;

  @Column(name = "is_migration")
  private Boolean isMigration;

  @OneToMany(mappedBy = "product")
  private Set<ProductEngId> productEngs = new LinkedHashSet<>();

  @OneToMany(mappedBy = "product")
  private Set<ProductProductName> productProductNames = new LinkedHashSet<>();

  @OneToMany(mappedBy = "product")
  private Set<ProductRole> productRoles = new LinkedHashSet<>();

  public Set<ProductRole> getProductRoles() {
    return productRoles;
  }

  public void setProductRoles(Set<ProductRole> productRoles) {
    this.productRoles = productRoles;
  }

  public Set<ProductProductName> getProductProductNames() {
    return productProductNames;
  }

  public void setProductProductNames(Set<ProductProductName> productProductNames) {
    this.productProductNames = productProductNames;
  }

  public Set<ProductEngId> getProductEngs() {
    return productEngs;
  }

  public void setProductEngs(Set<ProductEngId> productEngs) {
    this.productEngs = productEngs;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public SubscriptionDefinition getSubscriptionDefinition() {
    return subscriptionDefinition;
  }

  public void setSubscriptionDefinition(SubscriptionDefinition subscriptionDefinition) {
    this.subscriptionDefinition = subscriptionDefinition;
  }

  public String getLevel1() {
    return level1;
  }

  public void setLevel1(String level1) {
    this.level1 = level1;
  }

  public String getLevel2() {
    return level2;
  }

  public void setLevel2(String level2) {
    this.level2 = level2;
  }

  public Boolean getIsMigration() {
    return isMigration;
  }

  public void setIsMigration(Boolean isMigration) {
    this.isMigration = isMigration;
  }
}
