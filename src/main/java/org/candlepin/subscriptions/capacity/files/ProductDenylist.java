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
package org.candlepin.subscriptions.capacity.files;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.PerLineFileSource;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** List of products to be skipped for capacity calculations. */
@Slf4j
@Component
public class ProductDenylist implements ResourceLoaderAware {
  private static final List<String> suffixes =
      Stream.of("F2", "F3", "F3RN", "F4", "F5", "HR", "MO", "RN", "S")
          // sort the suffixes by length descending, so that e.g. F3RN is found before RN.
          .sorted(Comparator.comparing(s -> -s.length()))
          .toList();

  public static List<String> getSuffixes() {
    return suffixes;
  }

  private final PerLineFileSource source;

  public ProductDenylist(ApplicationProperties properties, ApplicationClock clock) {
    if (StringUtils.hasText(properties.getProductDenylistResourceLocation())) {
      source =
          new PerLineFileSource(
              properties.getProductDenylistResourceLocation(),
              clock.getClock(),
              properties.getProductDenyListCacheTtl());
    } else {
      source = null;
    }
  }

  public boolean productIdMatches(String productId) {
    if (source == null) {
      return false;
    }
    try {
      var normalizedSku = removeSuffix(productId);
      boolean isDenylisted = source.set().contains(normalizedSku);
      if (isDenylisted && log.isDebugEnabled()) {
        log.debug("Product ID {} in denylist", productId);
      }
      return isDenylisted;
    } catch (Exception e) {
      log.error("Error reading denylist", e);
      return false;
    }
  }

  private String removeSuffix(String productId) {
    for (String suffix : suffixes) {
      if (productId.endsWith(suffix)) {
        return productId.substring(0, productId.length() - suffix.length());
      }
    }
    return productId;
  }

  /**
   * Lists all products allowed by the list.
   *
   * @return a set of all allowed products, or an empty set if no products are allowed or no source
   *     was specified.
   */
  public Set<String> allProducts() {
    if (source == null) {
      log.warn("No source exists.");
      return Collections.emptySet();
    }

    try {
      return Collections.unmodifiableSet(source.set());
    } catch (IOException e) {
      log.error("Error reading denylist", e);
      return Collections.emptySet();
    }
  }

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    if (source != null) {
      source.setResourceLoader(resourceLoader);
    }
  }

  @PostConstruct
  public void init() {
    if (source != null) {
      source.init();
    }
  }
}
