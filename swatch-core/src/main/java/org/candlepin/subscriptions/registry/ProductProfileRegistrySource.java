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
package org.candlepin.subscriptions.registry;

import java.io.InputStream;
import org.candlepin.subscriptions.files.YamlFileSource;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Class to load product profile registry used to define how capacities and tallies are calculated.
 */
public class ProductProfileRegistrySource extends YamlFileSource<ProductProfileRegistry>
    implements FactoryBean<ProductProfileRegistry> {
  private static final Logger log = LoggerFactory.getLogger(ProductProfileRegistrySource.class);

  public ProductProfileRegistrySource(ProductProfileProperties properties, ApplicationClock clock) {
    super(
        properties.getProductProfileRegistryResourceLocation(),
        clock.getClock(),
        properties.getProductProfileListCacheTtl(),
        properties.isStrictProfileLoadingMode());
  }

  @Override
  protected ProductProfileRegistry getDefault() {
    return ProductProfileRegistry.getDefaultRegistry();
  }

  @Override
  protected ProductProfileRegistry parse(InputStream s) {
    Yaml parser = new Yaml(new Constructor(ProductProfile.class));
    ProductProfileRegistry registry = new ProductProfileRegistry();
    for (Object o : parser.loadAll(s)) {
      if (o instanceof ProductProfile) {
        registry.addProductProfile((ProductProfile) o);
      } else {
        log.warn("Unknown object {} in product profile YAML.", o);
      }
    }
    return registry;
  }

  @Override
  public ProductProfileRegistry getObject() throws Exception {
    return getValue();
  }

  @Override
  public Class<?> getObjectType() {
    return ProductProfileRegistry.class;
  }

  @Override
  public boolean isSingleton() {
    return false;
  }
}
