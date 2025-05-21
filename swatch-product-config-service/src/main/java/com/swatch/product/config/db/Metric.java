package com.swatch.product.config.db;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

@ToString
@Entity
@Table(
    name = "metric",
    schema = "swatch_product_config",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "metrics_id_subscription_id_key",
          columnNames = {"id", "subscription_id"})
    })
public class Metric {
  @Id
  @ColumnDefault("gen_random_uuid()")
  @Column(name = "uuid", nullable = false)
  private UUID id;

  @NotNull
  @Column(name = "id", nullable = false, length = Integer.MAX_VALUE)
  private String id1;

  @ToString.Exclude
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private SubscriptionDefinition subscriptionDefinition;

  @Column(name = "rhm_metric_id", length = Integer.MAX_VALUE)
  private String rhmMetricId;

  @Column(name = "aws_dimension", length = Integer.MAX_VALUE)
  private String awsDimension;

  @Column(name = "azure_dimension", length = Integer.MAX_VALUE)
  private String azureDimension;

  @ColumnDefault("1")
  @Column(name = "billing_factor", precision = 10, scale = 4)
  private BigDecimal billingFactor;

  @Column(name = "enable_gratis_usage")
  private Boolean enableGratisUsage;

  @Column(name = "prometheus", length = Integer.MAX_VALUE)
  private String prometheus;

  @Column(name = "type", length = Integer.MAX_VALUE)
  private String type;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getId1() {
    return id1;
  }

  public void setId1(String id1) {
    this.id1 = id1;
  }

  public SubscriptionDefinition getSubscriptionDefinition() {
    return subscriptionDefinition;
  }

  public void setSubscriptionDefinition(SubscriptionDefinition subscriptionDefinition) {
    this.subscriptionDefinition = subscriptionDefinition;
  }

  public String getRhmMetricId() {
    return rhmMetricId;
  }

  public void setRhmMetricId(String rhmMetricId) {
    this.rhmMetricId = rhmMetricId;
  }

  public String getAwsDimension() {
    return awsDimension;
  }

  public void setAwsDimension(String awsDimension) {
    this.awsDimension = awsDimension;
  }

  public String getAzureDimension() {
    return azureDimension;
  }

  public void setAzureDimension(String azureDimension) {
    this.azureDimension = azureDimension;
  }

  public BigDecimal getBillingFactor() {
    return billingFactor;
  }

  public void setBillingFactor(BigDecimal billingFactor) {
    this.billingFactor = billingFactor;
  }

  public Boolean getEnableGratisUsage() {
    return enableGratisUsage;
  }

  public void setEnableGratisUsage(Boolean enableGratisUsage) {
    this.enableGratisUsage = enableGratisUsage;
  }

  public String getPrometheus() {
    return prometheus;
  }

  public void setPrometheus(String prometheus) {
    this.prometheus = prometheus;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
