package com.swatch.product.config.db;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

@ToString
@Entity
@Table(name = "product_eng_id", schema = "swatch_product_config")
public class ProductEngId {
  @Id
  @ColumnDefault("gen_random_uuid()")
  @Column(name = "id", nullable = false)
  private UUID id;

  @NotNull
  @Column(name = "eng_id", nullable = false)
  private Integer engId;

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

  public Integer getEngId() {
    return engId;
  }

  public void setEngId(Integer engId) {
    this.engId = engId;
  }

  public Product getProduct() {
    return product;
  }

  public void setProduct(Product product) {
    this.product = product;
  }
}
