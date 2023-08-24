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
package org.candlepin.subscriptions.metering.service.prometheus.promql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import java.util.HashMap;
import java.util.Map;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryBuilderTest {

  Metric tag;
  final String TEST_PROD_TAG = "OpenShift-dedicated-metrics";

  @BeforeEach
  void setupTest() {
    var subDefOptional = SubscriptionDefinition.lookupSubscriptionByTag(TEST_PROD_TAG);
    subDefOptional
        .flatMap(subDef -> subDef.getMetric(Measurement.Uom.CORES.value()))
        .ifPresent(tag -> this.tag = tag);
  }

  @Test
  void testBuildQuery() {
    String templateKey = "default";
    String template =
        "Account: #{runtime[account]} Metric ID: #{metric.id} P1: #{metric.prometheus.queryParams[metadataMetric]}";

    String id = "Cores";
    String account = "12345";
    String param1 = "ocm_subscription";

    MetricProperties props = new MetricProperties();
    props.getQueryTemplates().put(templateKey, template);

    Map<String, String> params = new HashMap<>();
    params.put("metadataMetric", param1);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put("account", account);

    QueryDescriptor queryDesc = new QueryDescriptor(tag);
    queryDesc.addRuntimeVar("account", account);

    QueryBuilder builder = new QueryBuilder(props);
    assertEquals(
        String.format("Account: %s Metric ID: %s P1: %s", account, id, param1),
        builder.build(queryDesc));
  }

  @Test
  void testExceptionWhenInvalidTemplateSpecified() {
    String key = "default";
    QueryBuilder builder = new QueryBuilder(new MetricProperties());
    QueryDescriptor descriptor = new QueryDescriptor(tag);
    Throwable e = assertThrows(IllegalArgumentException.class, () -> builder.build(descriptor));

    assertEquals(String.format("Unable to find query template for key: %s", key), e.getMessage());
  }
}
