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
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
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
class SubscriptionDefinitionRegistry {

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

    // Get all resources on the classpath under the subscription_configs subdirectory.
    String resourceDirectory = "subscription_configs";
    URL resourceUrl = Objects.requireNonNull(classLoader.getResource(resourceDirectory));

    Path directoryPath;

    // Needed to support AppClassLoader for when a fancy Quarkus ClassLoader isn't available
    if (Objects.equals(resourceUrl.getProtocol(), "jar")) {
      FileSystem fileSystem;
      try {
        fileSystem = FileSystems.newFileSystem(resourceUrl.toURI(), Collections.emptyMap());
      } catch (IOException | URISyntaxException e) {
        throw new ConfigResourcesLoadingException(e);
      }
      directoryPath = fileSystem.getPath(resourceDirectory);
    } else {
      try {
        directoryPath = Paths.get(resourceUrl.toURI());
      } catch (URISyntaxException e) {
        throw new ConfigResourcesLoadingException(e);
      }
    }

    log.debug("Attempting to walk directoryPath: {}", directoryPath);

    // List YAML files in the resource directory
    try (Stream<Path> paths = Files.walk(directoryPath)) {
      paths
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(".yaml"))
          .forEach(
              file -> {
                try (InputStream inputStream = Files.newInputStream(file)) {

                  var subscriptionFromYaml = yaml.loadAs(inputStream, SubscriptionDefinition.class);
                  subscriptionFromYaml
                      .getVariants()
                      .forEach(variant -> variant.setSubscription(subscriptionFromYaml));
                  subscriptions.add(subscriptionFromYaml);

                } catch (IOException e) {
                  throw new ConfigResourcesLoadingException(e);
                }
              });
    } catch (IOException e) {
      log.error("Error while listing files. {}", e.getMessage());
      throw new ConfigResourcesLoadingException(e);
    }
  }
}
