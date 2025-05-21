package com.swatch.product.config.db;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

@ToString
@Entity
@Table(name = "product_product_name", schema = "swatch_product_config")
public class ProductProductName {
  @Id
  @ColumnDefault("gen_random_uuid()")
  @Column(name = "id", nullable = false)
  private UUID id;

  @NotNull
  @Column(name = "product_name", nullable = false, length = Integer.MAX_VALUE)
  private String productName;

  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  private Product product;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
  }

  public Product getProduct() {
    return product;
  }

  public void setProduct(Product product) {
    this.product = product;
  }
}
