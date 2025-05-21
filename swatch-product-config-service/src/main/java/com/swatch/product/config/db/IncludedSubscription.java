package com.swatch.product.config.db;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "included_subscription", schema = "swatch_product_config")
public class IncludedSubscription {
  @Id
  @ColumnDefault("gen_random_uuid()")
  @Column(name = "id", nullable = false)
  private UUID id;

  @ToString.Exclude
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private SubscriptionDefinition subscriptionDefinition;

  @NotNull
  @Column(name = "included_id", nullable = false, length = Integer.MAX_VALUE)
  private String includedId;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public SubscriptionDefinition getSubscription() {
    return subscriptionDefinition;
  }

  public void setSubscription(SubscriptionDefinition subscriptionDefinition) {
    this.subscriptionDefinition = subscriptionDefinition;
  }

  public String getIncludedId() {
    return includedId;
  }

  public void setIncludedId(String includedId) {
    this.includedId = includedId;
  }
}
