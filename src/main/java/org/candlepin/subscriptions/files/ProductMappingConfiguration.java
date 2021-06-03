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
package org.candlepin.subscriptions.files;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/** Configuration that provides the product ID to product map. */
@ComponentScan(basePackages = "org.candlepin.subscriptions.files")
public class ProductMappingConfiguration {

  @Bean
  public ProductProfileRegistrySource productProfileRegistrySource(
      ApplicationProperties applicationProperties, ApplicationClock applicationClock) {
    return new ProductProfileRegistrySource(applicationProperties, applicationClock);
  }

  @Bean
  public TagProfile tagProfile() throws FileNotFoundException {
    Yaml parser = new Yaml(new Constructor(TagProfile.class));
    return parser.load(new FileInputStream(ResourceUtils.getFile("classpath:tag_profile.yaml")));
  }
}
