package com.swatch.product.config;

import java.util.Objects;
import java.util.Set;

public class ProductTagLookupParams {
    public Set<Integer> engIds;
    public String role;
    public String productName;
    public Set<String> metricIds;
    public Boolean isPaygEligibleProduct;
    public Boolean is3rdPartyMigration;
    public String productTag;
    public String level1;
    public String level2;

    public Set<Integer> getEngIds() {
        return engIds;
    }

    public void setEngIds(Set<Integer> engIds) {
        this.engIds = engIds;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Set<String> getMetricIds() {
        return metricIds;
    }

    public void setMetricIds(Set<String> metricIds) {
        this.metricIds = metricIds;
    }

    public Boolean getIsPaygEligibleProduct() {
        return isPaygEligibleProduct;
    }

    public void setIsPaygEligibleProduct(Boolean isPaygEligibleProduct) {
        this.isPaygEligibleProduct = isPaygEligibleProduct;
    }

    public Boolean getIs3rdPartyMigration() {
        return is3rdPartyMigration;
    }

    public void setIs3rdPartyMigration(Boolean is3rdPartyMigration) {
        this.is3rdPartyMigration = is3rdPartyMigration;
    }

    public String getProductTag() {
        return productTag;
    }

    public void setProductTag(String productTag) {
        this.productTag = productTag;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductTagLookupParams that = (ProductTagLookupParams) o;
        return Objects.equals(engIds, that.engIds) &&
                Objects.equals(role, that.role) &&
                Objects.equals(productName, that.productName) &&
                Objects.equals(metricIds, that.metricIds) &&
                Objects.equals(isPaygEligibleProduct, that.isPaygEligibleProduct) &&
                Objects.equals(is3rdPartyMigration, that.is3rdPartyMigration) &&
                Objects.equals(productTag, that.productTag) &&
                Objects.equals(level1, that.level1) &&
                Objects.equals(level2, that.level2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(engIds, role, productName, metricIds, isPaygEligibleProduct,
                is3rdPartyMigration, productTag, level1, level2);
    }

    @Override
    public String toString() {
        return "ProductTagLookupParams{" +
                "engIds=" + engIds +
                ", role='" + role + '\'' +
                ", productName='" + productName + '\'' +
                ", metricIds=" + metricIds +
                ", isPaygEligibleProduct=" + isPaygEligibleProduct +
                ", is3rdPartyMigration=" + is3rdPartyMigration +
                ", productTag='" + productTag + '\'' +
                ", level1='" + level1 + '\'' +
                ", level2='" + level2 + '\'' +
                '}';
    }
}
