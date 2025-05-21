package com.swatch.product.config.db;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

@ToString
@Entity
@Table(
    name = "subscription_default",
    schema = "swatch_product_config",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "subscription_default_subscription_id_key",
          columnNames = {"subscription_id"})
    })
public class SubscriptionDefault {
  @Id
  @ColumnDefault("gen_random_uuid()")
  @Column(name = "id", nullable = false)
  private UUID id;

  @ToString.Exclude
  @NotNull
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private SubscriptionDefinition subscriptionDefinition;

  @Column(name = "product_id", length = Integer.MAX_VALUE)
  private String productId;

  @Column(name = "sla", length = Integer.MAX_VALUE)
  private String sla;

  @Column(name = "usage", length = Integer.MAX_VALUE)
  private String usage;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public SubscriptionDefinition getSubscriptionDefinition() {
    return subscriptionDefinition;
  }

  public void setSubscriptionDefinition(SubscriptionDefinition subscriptionDefinition) {
    this.subscriptionDefinition = subscriptionDefinition;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public String getSla() {
    return sla;
  }

  public void setSla(String sla) {
    this.sla = sla;
  }

  public String getUsage() {
    return usage;
  }

  public void setUsage(String usage) {
    this.usage = usage;
  }
}
