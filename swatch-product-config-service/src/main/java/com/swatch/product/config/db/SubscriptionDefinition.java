package com.swatch.product.config.db;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.Set;

@ToString
@Entity
@Table(name = "subscription", schema = "swatch_product_config")
public class SubscriptionDefinition {


  @Id
  @Column(name = "id", nullable = false, length = Integer.MAX_VALUE)
  private String id;

  @NotNull
  @Column(name = "platform", nullable = false, length = Integer.MAX_VALUE)
  private String platform;

  @Column(name = "service_type", length = Integer.MAX_VALUE)
  private String serviceType;

  @Column(name = "contract_enabled")
  private Boolean contractEnabled;

  @Column(name = "vdc_type")
  private Boolean vdcType;

  @Column(name = "is_payg")
  private Boolean isPayg;

  @OneToMany(mappedBy = "subscriptionDefinition")
  private Set<IncludedSubscription> includedSubscriptions = new LinkedHashSet<>();

  @OneToMany(mappedBy = "subscriptionDefinition")
  private Set<Metric> metrics = new LinkedHashSet<>();

  @OneToMany(mappedBy = "subscriptionDefinition")
  private Set<Product> products = new LinkedHashSet<>();

  @OneToOne(mappedBy = "subscriptionDefinition")
  private com.swatch.product.config.db.SubscriptionDefault subscriptionDefault;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getServiceType() {
    return serviceType;
  }

  public void setServiceType(String serviceType) {
    this.serviceType = serviceType;
  }

  public Boolean getContractEnabled() {
    return contractEnabled;
  }

  public void setContractEnabled(Boolean contractEnabled) {
    this.contractEnabled = contractEnabled;
  }

  public Boolean getVdcType() {
    return vdcType;
  }

  public void setVdcType(Boolean vdcType) {
    this.vdcType = vdcType;
  }

  public Boolean getIsPayg() {
    return isPayg;
  }

  public void setIsPayg(Boolean isPayg) {
    this.isPayg = isPayg;
  }

  public Set<IncludedSubscription> getIncludedSubscriptions() {
    return includedSubscriptions;
  }

  public void setIncludedSubscriptions(Set<IncludedSubscription> includedSubscriptions) {
    this.includedSubscriptions = includedSubscriptions;
  }

  public Set<Metric> getMetrics() {
    return metrics;
  }

  public void setMetrics(Set<Metric> metrics) {
    this.metrics = metrics;
  }

  public Set<Product> getProducts() {
    return products;
  }

  public void setProducts(Set<Product> products) {
    this.products = products;
  }

  public com.swatch.product.config.db.SubscriptionDefault getSubscriptionDefault() {
    return subscriptionDefault;
  }

  public void setSubscriptionDefault(
      com.swatch.product.config.db.SubscriptionDefault subscriptionDefault) {
    this.subscriptionDefault = subscriptionDefault;
  }


}
