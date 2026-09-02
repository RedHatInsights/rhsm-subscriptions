/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.component.tests.api.hbi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Facts reported by the QPC/discovery reporter (HBI namespace {@code qpc}).
 *
 * <p>Only models facts that are actually read by HBI event normalization (see {@code QpcFacts} in
 * {@code swatch-model-metrics-hbi}). Other facts seen in real payloads (e.g. {@code IS_RHEL},
 * {@code last_discovered}) are not normalized and are intentionally omitted.
 */
@Getter
public final class QpcFacts {

  private static final String PRODUCT_ID_FACT = "rh_products_installed";

  private final List<String> products;

  // Fully-qualified: a bare `@Builder` here would resolve to the nested Builder class below
  // instead of lombok.Builder, since a member type shadows a same-named import in its own body.
  @lombok.Builder(builderClassName = "Builder")
  private QpcFacts(List<String> products) {
    this.products = products;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Convert to the raw key/value map HBI expects for the {@code qpc} facts namespace. Only fields
   * that were explicitly set are included.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> facts = new LinkedHashMap<>();
    if (products != null) {
      facts.put(PRODUCT_ID_FACT, products);
    }
    return facts;
  }

  public static class Builder {
    /** Seed the common QPC defaults used across component tests (RHEL detected). */
    public Builder defaultFacts() {
      this.products = List.of("69");
      return this;
    }
  }
}
