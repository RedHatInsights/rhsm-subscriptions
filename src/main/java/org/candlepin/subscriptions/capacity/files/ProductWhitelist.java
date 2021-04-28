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

import javax.annotation.PostConstruct;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.PerLineFileSource;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** List of products to be considered for capacity calculations. */
@Component
public class ProductWhitelist implements ResourceLoaderAware {

  private static Logger log = LoggerFactory.getLogger(ProductWhitelist.class);

  private final PerLineFileSource source;

  public ProductWhitelist(ApplicationProperties properties, ApplicationClock clock) {
    if (StringUtils.hasText(properties.getProductWhitelistResourceLocation())) {
      source =
          new PerLineFileSource(
              properties.getProductWhitelistResourceLocation(),
              clock.getClock(),
              properties.getProductWhiteListCacheTtl());
    } else {
      source = null;
    }
  }

  public boolean productIdMatches(String productId) {
    if (source == null) {
      return true;
    }
    try {
      boolean whitelisted = source.set().contains(productId);
      if (!whitelisted && log.isDebugEnabled()) {
        log.debug("Product ID {} not in whitelist", productId);
      }
      return whitelisted;
    } catch (Exception e) {
      log.error("Error reading whitelist", e);
      return false;
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
