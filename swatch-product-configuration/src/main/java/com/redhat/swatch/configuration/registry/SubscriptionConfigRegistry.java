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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

class SubscriptionConfigRegistry {

  private static SubscriptionConfigRegistry instance = null;

  @Getter private final List<Subscription> subscriptions;

  public static synchronized SubscriptionConfigRegistry getInstance() throws IOException {
    if (instance == null) {
      instance = new SubscriptionConfigRegistry();
    }
    return instance;
  }

  SubscriptionConfigRegistry() throws IOException {
    subscriptions = new ArrayList<>();

    Representer representer = new Representer();
    representer.getPropertyUtils().setSkipMissingProperties(true);
    Yaml yaml = new Yaml(representer);

    for (String fileName : listYamlFileNames()) {
      try (InputStream inputStream =
          this.getClass().getClassLoader().getResourceAsStream(fileName)) {
        var subscriptionFromYaml = yaml.loadAs(inputStream, Subscription.class);
        subscriptions.add(subscriptionFromYaml);
      }
    }
  }

  private static List<String> listYamlFileNames() throws IOException {

    var yamlFiles = new ArrayList<String>();

    // Get the class loader
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    // Get all resources on the classpath under the subscription_configs subdirectory
    Enumeration<URL> resources = classLoader.getResources("subscription_configs");

    while (resources.hasMoreElements()) {
      URL resourceUrl = resources.nextElement();

      // List YAML files in the resource directory
      try {
        Path directoryPath = Paths.get(resourceUrl.getFile());
        Files.walk(directoryPath)
            .filter(Files::isRegularFile)
            .filter(file -> file.getFileName().toString().endsWith(".yaml"))
            .forEach(
                file -> {
                  String fileName = file.toString();
                  int thirdLastIndex =
                      fileName.lastIndexOf(
                          "/", fileName.lastIndexOf("/", fileName.lastIndexOf("/") - 1) - 1);
                  if (thirdLastIndex >= 0) {
                    String trimmedFileName = fileName.substring(thirdLastIndex + 1);
                    yamlFiles.add(trimmedFileName);
                  }
                });
      } catch (IOException e) {
        // TODO log this for real
        System.err.println("Error while listing files: " + e.getMessage());
      }
    }
    return yamlFiles;
  }

  public Optional<Subscription> findByServiceType(String serviceType) {

    return subscriptions.stream()
        // TODO filter
        .findFirst();
  }
}
