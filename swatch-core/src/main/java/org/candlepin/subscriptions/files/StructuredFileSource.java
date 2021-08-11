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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Abstract class for loading data from a structured file on the classpath or filesystem.
 *
 * @param <T> Expected return type for the loaded file.
 */
public abstract class StructuredFileSource<T> implements ResourceLoaderAware {
  private static final Logger log = LoggerFactory.getLogger(StructuredFileSource.class);

  private final Cache<T> cachedValue;
  private final String resourceLocation;
  private ResourceLoader resourceLoader = new DefaultResourceLoader();
  private Resource fileResource;

  protected StructuredFileSource(String resourceLocation, Clock clock, Duration cacheTtl) {
    log.debug("Opening file source at {}", resourceLocation);
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

  protected abstract T parse(InputStream s) throws IOException;

  protected abstract T getDefault();

  /**
   * Parsing methods that need to know the class of generic type T can call this method (although
   * they will need to cast the return value to Class&gt;T&lt;). This method signature is the same
   * as Spring's FactoryBean interface which many subclasses of StructuredFileSource will implement.
   *
   * @return the type of object this StructuredFileSource builds
   */
  public abstract Class<?> getObjectType();

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
