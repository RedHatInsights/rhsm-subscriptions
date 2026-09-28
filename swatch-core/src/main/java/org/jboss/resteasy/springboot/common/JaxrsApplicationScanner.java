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
package org.jboss.resteasy.springboot.common;

import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Scans the classpath under the specified packages for JAX-RS {@link Application} subclasses.
 *
 * @author Fabio Carvalho (facarvalho@paypal.com or fabiocarvalho777@gmail.com)
 */
public abstract class JaxrsApplicationScanner {

  private static final Logger logger = LoggerFactory.getLogger(JaxrsApplicationScanner.class);

  private static final Map<String, Set<Class<? extends Application>>> packagesToClassesMap =
      new ConcurrentHashMap<>();

  public static Set<Class<? extends Application>> getApplications(
      List<String> packagesToBeScanned) {
    ClassLoader classLoader = resolveClassLoader();
    final String packagesKey = createPackagesKey(packagesToBeScanned, classLoader);
    return packagesToClassesMap.computeIfAbsent(
        packagesKey, key -> findJaxrsApplicationClasses(packagesToBeScanned, classLoader));
  }

  private static ClassLoader resolveClassLoader() {
    ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
    return classLoader != null ? classLoader : JaxrsApplicationScanner.class.getClassLoader();
  }

  private static String createPackagesKey(
      List<String> packagesToBeScanned, ClassLoader classLoader) {
    return String.join(",", packagesToBeScanned) + "|" + System.identityHashCode(classLoader);
  }

  /*
   * Scan the classpath under the specified packages looking for JAX-RS Application sub-classes
   */
  private static Set<Class<? extends Application>> findJaxrsApplicationClasses(
      List<String> packagesToBeScanned, ClassLoader classLoader) {
    logger.info("Scanning classpath to find JAX-RS Application classes");

    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.setResourceLoader(new DefaultResourceLoader(classLoader));
    scanner.addIncludeFilter(new AssignableTypeFilter(Application.class));

    Set<BeanDefinition> candidates = new HashSet<BeanDefinition>();
    Set<BeanDefinition> candidatesSubSet;

    for (String packageToScan : packagesToBeScanned) {
      candidatesSubSet = scanner.findCandidateComponents(packageToScan);
      candidates.addAll(candidatesSubSet);
    }

    Set<Class<? extends Application>> classes = new HashSet<Class<? extends Application>>();
    Class<? extends Application> type;
    for (BeanDefinition candidate : candidates) {
      try {
        type =
            (Class<? extends Application>)
                ClassUtils.forName(candidate.getBeanClassName(), classLoader);
        classes.add(type);
      } catch (ClassNotFoundException e) {
        logger.error("JAX-RS Application subclass could not be loaded", e);
      }
    }

    // We don't want the JAX-RS Application class itself in there
    classes.remove(Application.class);

    return classes;
  }
}
