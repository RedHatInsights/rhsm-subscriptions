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

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import javax.annotation.PostConstruct;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;

/**
 * Abstract class for loading data from a YAML file on the classpath or filesystem.
 *
 * @param <T> Expected return type for the loaded yaml.
 */
public abstract class YamlFileSource<T> implements ResourceLoaderAware {

  private final Cache<T> cachedValue;
  private String resourceLocation;
  private ResourceLoader resourceLoader = new DefaultResourceLoader();
  private Resource fileResource;

  protected YamlFileSource(String resourceLocation, Clock clock, Duration cacheTtl) {
    this.resourceLocation = resourceLocation;
    this.cachedValue = new Cache(clock, cacheTtl);
  }

  public T getValue() throws IOException {
    if (cachedValue.isExpired()) {
      try (InputStream s = fileResource.getInputStream()) {
        T value = parse(s);
        if (value == null) {
          return getDefault();
        }
        cachedValue.setValue(value);
      }
    }
    return cachedValue.getValue();
  }

  /**
   * Allow subclasses to redefine how the YAML for type T is deserialized
   *
   * @param s InputStream with the YAML
   * @return an object of type T constructed from the YAML in InputStream s
   */
  protected T parse(InputStream s) {
    return new Yaml().load(s);
  }

  protected abstract T getDefault();

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  public void init() {
    fileResource = resourceLoader.getResource(resourceLocation);
    if (!fileResource.exists()) {
      throw new IllegalStateException("Resource not found: " + fileResource.getDescription());
    }
  }
}
