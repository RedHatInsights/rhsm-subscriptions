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

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Collects each line of a file and returns it as a List of Strings. Empty strings are ignored. */
public class PerLineFileSource implements ResourceLoaderAware {

  private final Cache<List<String>> listCache;
  private final Cache<Set<String>> setCache;
  private ResourceLoader resourceLoader = new DefaultResourceLoader();
  private Resource fileResource;
  private String resourceLocation;

  public PerLineFileSource(String resourceLocation, Clock clock, Duration cacheTtl) {
    this.resourceLocation = resourceLocation;
    this.listCache = new Cache<>(clock, cacheTtl);
    this.setCache = new Cache<>(clock, cacheTtl);
  }

  public List<String> list() throws IOException {
    return getCachedValue(listCache, Collectors.toList());
  }

  public Set<String> set() throws IOException {
    return getCachedValue(setCache, Collectors.toSet());
  }

  private <T extends Collection<String>> T getCachedValue(
      Cache<T> cache, Collector<String, ?, T> collector) throws IOException {

    if (cache.isExpired()) {
      try (InputStream s = fileResource.getInputStream()) {
        cache.setValue(
            new BufferedReader(new InputStreamReader(s, Charset.defaultCharset()))
                .lines()
                .filter(line -> line != null && !line.isEmpty())
                .collect(collector));
      }
    }
    return cache.getValue();
  }

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
