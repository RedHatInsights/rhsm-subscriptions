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
package com.redhat.swatch.configuration.registry;

import com.redhat.swatch.configuration.exception.ConfigResourcesLoadingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Loads yaml files from src/main/resource/subscription_configs into List<Subscription>. Provides
 * lookup methods for that list.
 */
@Slf4j
public class SubscriptionDefinitionRegistry {

  private static SubscriptionDefinitionRegistry instance = null;

  @Getter private final List<SubscriptionDefinition> subscriptions;

  public static synchronized SubscriptionDefinitionRegistry getInstance() {
    if (instance == null) {
      instance = new SubscriptionDefinitionRegistry();
    }
    return instance;
  }

  SubscriptionDefinitionRegistry() {
    subscriptions = new ArrayList<>();
    Constructor constructor = new Constructor(SubscriptionDefinition.class, new LoaderOptions());
    constructor.getPropertyUtils().setSkipMissingProperties(true);

    Yaml yaml = new Yaml(constructor);

    // Get the class loader
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    String[] configFiles;
    // Get index of yaml files to load
    try (var index = classLoader.getResourceAsStream("swatch_config_index.txt")) {
      if (index == null) {
        throw new ConfigResourcesLoadingException(
            new IllegalStateException("config yaml index could not be read"));
      }
      String contents = new String(index.readAllBytes(), StandardCharsets.UTF_8);
      configFiles = contents.split("\n");
    } catch (IOException e) {
      throw new ConfigResourcesLoadingException(e);
    }

    for (String resource : configFiles) {
      try (var inputStream = classLoader.getResourceAsStream(resource)) {
        var subscriptionFromYaml = yaml.loadAs(inputStream, SubscriptionDefinition.class);
        subscriptionFromYaml
            .getVariants()
            .forEach(variant -> variant.setSubscription(subscriptionFromYaml));
        subscriptions.add(subscriptionFromYaml);
      } catch (IOException e) {
        throw new ConfigResourcesLoadingException(e);
      }
    }
  }
}
