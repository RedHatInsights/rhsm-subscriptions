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
package com.redhat.swatch.contract.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class ProductDenylist {
  public static final String CLASSPATH_URL_PREFIX = "classpath:";
  public static final String FILE_URL_PREFIX = "file:";

  public static final List<String> SUFFIXES =
      Stream.of("F2", "F3", "F3RN", "F4", "F5", "HR", "MO", "RN", "S")
          // sort the suffixes by length descending, so that e.g. F3RN is found before RN.
          .sorted(Comparator.comparing(s -> -s.length()))
          .collect(Collectors.toUnmodifiableList());

  // Taken from Spring's ClassUtils
  /**
   * Return the default ClassLoader to use: typically the thread context ClassLoader, if available;
   * the ClassLoader that loaded the ClassUtils class will be used as fallback.
   *
   * <p>Call this method if you intend to use the thread context ClassLoader in a scenario where you
   * clearly prefer a non-null ClassLoader reference: for example, for class path resource loading
   * (but not necessarily for {@code Class.forName}, which accepts a {@code null} ClassLoader
   * reference as well).
   *
   * @return the default ClassLoader (only {@code null} if even the system ClassLoader isn't
   *     accessible)
   * @see Thread#getContextClassLoader()
   * @see ClassLoader#getSystemClassLoader()
   */
  protected static ClassLoader getDefaultClassLoader() {
    ClassLoader cl = null;
    try {
      cl = Thread.currentThread().getContextClassLoader();
    } catch (Throwable ex) { // NOSONAR
      // Cannot access thread context ClassLoader - falling back...
    }
    if (cl == null) {
      // No thread context class loader -> use class loader of this class.
      cl = ProductDenylist.class.getClassLoader();
      if (cl == null) {
        // getClassLoader() returning null indicates the bootstrap ClassLoader
        try {
          cl = ClassLoader.getSystemClassLoader();
        } catch (Throwable ex) { // NOSONAR
          // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
        }
      }
    }
    return cl;
  }

  /**
   * Read the set of denied product SKUs from a file, one SKU per line.
   *
   * @param location the location of the file to read. The location can be prefixed with
   *     "classpath:" to read from the classpath or "file:" to read from the file system If no
   *     prefix is given, the location is assumed to be on the file system. This feature is meant to
   *     allow rudimentary interoperability with Spring's ResourceLoader API.
   * @return the set of SKUs in the given file
   * @throws IOException upon any read error
   */
  protected static Set<String> loadSet(String location) throws IOException {
    Reader resourceReader;

    if (location.startsWith(FILE_URL_PREFIX)) {
      location = location.substring(FILE_URL_PREFIX.length());
    }

    if (location.startsWith(CLASSPATH_URL_PREFIX)) {
      resourceReader = getResourceByClassloader(location.substring(CLASSPATH_URL_PREFIX.length()));
    } else if (location.startsWith("/")) {
      resourceReader = getResourceByPath(location);
    } else {
      throw new IOException(
          "Location \""
              + location
              + "\" should begin with either classpath:, "
              + "file:, or / and must be a fully qualified path");
    }

    try (BufferedReader r = new BufferedReader(resourceReader)) {
      return r.lines()
          .filter(line -> line != null && !line.isBlank())
          .collect(Collectors.toUnmodifiableSet());
    }
  }

  private static Reader getResourceByPath(String location) throws IOException {
    return new FileReader(location);
  }

  private static Reader getResourceByClassloader(String location) throws IOException {
    ClassLoader cl = ProductDenylist.getDefaultClassLoader();
    if (cl == null) {
      throw new IOException("Could not access classloader to load resource");
    }

    InputStream stream = cl.getResourceAsStream(location);
    if (stream == null) {
      throw new IOException("Could not load resource at " + location);
    }
    return new InputStreamReader(stream);
  }

  private final Set<String> skuSet;

  public ProductDenylist(
      @ConfigProperty(name = "PRODUCT_DENYLIST_RESOURCE_LOCATION")
          Optional<String> locationConfig) {
    String location = locationConfig.orElse("");

    // Allow specifying the empty string if no deny-list is desired
    if (!location.isEmpty()) {
      log.debug("Loading product denylist from {}", location);
      try {
        this.skuSet = ProductDenylist.loadSet(location);
      } catch (IOException e) {
        throw new UncheckedIOException("Error loading product denylist", e);
      }
    } else {
      log.warn("No denylist present in configuration");
      this.skuSet = Collections.emptySet();
    }
  }

  public boolean productIdMatches(String productId) {
    if (productId == null) {
      return false;
    }
    String normalizedSku = removeSuffix(productId);
    boolean isDenylisted = skuSet.contains(normalizedSku);
    if (isDenylisted) {
      log.debug("Product ID {} in denylist", productId);
    }
    return isDenylisted;
  }

  private String removeSuffix(String productId) {
    for (String suffix : SUFFIXES) {
      if (productId.endsWith(suffix)) {
        return productId.substring(0, productId.length() - suffix.length());
      }
    }
    return productId;
  }

  /**
   * Lists all products allowed by the list.
   *
   * @return a set of all allowed products, or an empty set if no products are allowed or no source
   *     was specified.
   */
  public Set<String> allProducts() {
    if (skuSet.isEmpty()) {
      log.warn("No denylist present in configuration");
    }
    return skuSet;
  }
}
