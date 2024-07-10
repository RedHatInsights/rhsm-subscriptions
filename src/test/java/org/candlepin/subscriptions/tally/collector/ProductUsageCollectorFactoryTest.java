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
package org.candlepin.subscriptions.tally.collector;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import java.awt.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductUsageCollectorFactoryTest {

  @Test
  void getRHELProductUsageCollector() {
    SubscriptionDefinition testDef = new SubscriptionDefinition();
    testDef.setVdcType(true);
    Variant testVariant = new Variant();
    testVariant.setTag("testProd1");
    testDef.setVariants(Set.of(testVariant));
    SubscriptionDefinitionRegistry.getInstance().getSubscriptions().add(testDef);
    assertThat(ProductUsageCollectorFactory.get("testProd1") instanceof RHELProductUsageCollector);
  }

  @Test
  void getDefaultProductUsageCollector() {
    SubscriptionDefinition testDef = new SubscriptionDefinition();
    testDef.setVdcType(false);
    Variant testVariant = new Variant();
    testVariant.setTag("testProd2");
    testDef.setVariants(Set.of(testVariant));
    SubscriptionDefinitionRegistry.getInstance().getSubscriptions().add(testDef);
    assertThat(
        ProductUsageCollectorFactory.get("testProd2") instanceof DefaultProductUsageCollector);
  }

  @Test
  void getNoVdcTypeSet() {
    SubscriptionDefinition testDef = new SubscriptionDefinition();
    // vdcType is null in testDef
    Variant testVariant = new Variant();
    testVariant.setTag("testProd3");
    testDef.setVariants(Set.of(testVariant));
    SubscriptionDefinitionRegistry.getInstance().getSubscriptions().add(testDef);
    assertThat(
        ProductUsageCollectorFactory.get("testProd3") instanceof DefaultProductUsageCollector);
  }
}
