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
package com.redhat.swatch.metrics.service;

import com.redhat.swatch.configuration.exception.ConfigResourcesLoadingException;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.yaml.snakeyaml.Yaml;

@ApplicationScoped
public class ProductConfigurationService {

  public Optional<String> getYamlByVariantTag(String productTag) {
    return SubscriptionDefinition.lookupSubscriptionByTag(productTag)
        .map(SubscriptionDefinition::getId)
        .flatMap(this::findYamlBySubscriptionId);
  }

  private Optional<String> findYamlBySubscriptionId(String subscriptionId) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    String[] configFiles;
    try (var index = classLoader.getResourceAsStream("swatch_config_index.txt")) {
      if (index == null) {
        throw new ConfigResourcesLoadingException(
            new IllegalStateException("config yaml index could not be read"));
      }
      configFiles = new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n");
    } catch (IOException e) {
      throw new ConfigResourcesLoadingException(e);
    }

    Yaml yaml = new Yaml();
    for (String resource : configFiles) {
      if (resource.isBlank()) {
        continue;
      }
      try (var inputStream = classLoader.getResourceAsStream(resource)) {
        if (inputStream == null) {
          continue;
        }
        byte[] bytes = inputStream.readAllBytes();
        Object loaded = yaml.load(new String(bytes, StandardCharsets.UTF_8));
        if (loaded instanceof Map<?, ?> map
            && Objects.equals(String.valueOf(map.get("id")), subscriptionId)) {
          return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        throw new ConfigResourcesLoadingException(e);
      }
    }
    return Optional.empty();
  }
}
