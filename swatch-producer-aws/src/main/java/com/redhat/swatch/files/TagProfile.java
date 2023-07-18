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
package com.redhat.swatch.files;

import com.redhat.swatch.exception.AwsDimensionNotConfiguredException;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

/** Provides product-specific configuration for AWS integration defined in tag_profile.yaml */
@ApplicationScoped
public class TagProfile {

  private final Map<TagMetricLookupKey, String> awsDimensionLookup;
  private final Map<TagMetricLookupKey, Double> billingFactorLookup;

  public TagProfile() {
    Representer representer = new Representer();
    // ignoring missing properties lets us couple this project to a smaller set of fields in
    // tag_profile.yaml
    representer.getPropertyUtils().setSkipMissingProperties(true);
    Yaml yaml = new Yaml(representer);
    InputStream tagProfileContents =
        getClass().getClassLoader().getResourceAsStream("tag_profile.yaml");
    TagProfileYaml tagProfileYaml = yaml.loadAs(tagProfileContents, TagProfileYaml.class);
    List<TagMetricYaml> tagMetricDefinitions = tagProfileYaml.getTagMetrics();
    awsDimensionLookup =
        tagMetricDefinitions.stream()
            .filter(m -> m.getAwsDimension() != null)
            .collect(Collectors.toMap(TagMetricLookupKey::new, TagMetricYaml::getAwsDimension));
    billingFactorLookup =
        tagMetricDefinitions.stream()
            .filter(m -> m.getBillingFactor() != null)
            .collect(Collectors.toMap(TagMetricLookupKey::new, TagMetricYaml::getBillingFactor));
  }

  public String getAwsDimension(String productId, String uom)
      throws AwsDimensionNotConfiguredException {
    String awsDimension = awsDimensionLookup.get(new TagMetricLookupKey(productId, uom));
    if (awsDimension == null) {
      throw new AwsDimensionNotConfiguredException(productId, uom);
    }
    return awsDimension;
  }

  public boolean isAwsConfigured(String product, String metric) {
    return awsDimensionLookup.containsKey(new TagMetricLookupKey(product, metric));
  }

  public Double getBillingFactor(String productTag, String metric) {
    return billingFactorLookup.getOrDefault(new TagMetricLookupKey(productTag, metric), 1.0);
  }

  @Data
  @AllArgsConstructor
  private static class TagMetricLookupKey {
    private String tag;
    private String metricId;

    TagMetricLookupKey(TagMetricYaml tagMetricDefinition) {
      this.tag = tagMetricDefinition.getTag();
      // NOTE: we plan to rename uom to metricId in ENT-4336
      this.metricId = tagMetricDefinition.getUom();
    }
  }
}
