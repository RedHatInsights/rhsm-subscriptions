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
package com.redhat.swatch.component.tests.core;

import static com.redhat.swatch.component.tests.logging.Log.LOG_FILE_PATH;
import static com.redhat.swatch.component.tests.logging.Log.LOG_SUFFIX;

import com.redhat.swatch.component.tests.configuration.BaseConfigurationBuilder;
import com.redhat.swatch.component.tests.configuration.ComponentTestConfiguration;
import com.redhat.swatch.component.tests.configuration.ComponentTestConfigurationBuilder;
import com.redhat.swatch.component.tests.configuration.ConfigurationLoader;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ComponentTestContext {

  private static final String GLOBAL = "global";
  private static final int COMPONENT_TEST_ID_MAX_SIZE = 60;

  private final ExtensionContext testContext;
  @Getter private final String id;
  private final Map<String, Object> customConfigurationByTarget = new HashMap<>();
  private final Map<Class<?>, List<Annotation>> annsForConfiguration = new HashMap<>();

  @Setter private ExtensionContext methodTestContext;
  private boolean failed;
  @Setter @Getter private boolean debug;

  ComponentTestContext(ExtensionContext testContext) {
    this.testContext = testContext;
    this.id = generateContextId(testContext);

    loadCustomConfiguration(GLOBAL, new ComponentTestConfigurationBuilder());
  }

  public boolean isFailed() {
    if (failed || testContext == null) {
      return failed;
    }

    // sometimes the failed flag has not been propagated yet, so we need to check the JUnit test
    // context
    return testContext.getExecutionException().isPresent();
  }

  public ComponentTestConfiguration getConfiguration() {
    return getConfigurationAs(ComponentTestConfiguration.class);
  }

  public <T> T getConfigurationAs(Class<T> configurationClazz) {
    return customConfigurationByTarget.values().stream()
        .filter(configurationClazz::isInstance)
        .map(configurationClazz::cast)
        .findFirst()
        .orElseThrow(
            () -> new RuntimeException("No found configuration for " + configurationClazz));
  }

  public String getRunningTestClassAndMethodName() {
    String classMethodName = getRunningTestClassName();
    Optional<String> methodName = getRunningTestMethodName();
    if (methodName.isPresent()) {
      classMethodName += "." + methodName.get();
    }

    return classMethodName;
  }

  public String getRunningTestClassName() {
    return getTestContext().getRequiredTestClass().getSimpleName();
  }

  public Optional<String> getRunningTestMethodName() {
    if (methodTestContext == null) {
      return Optional.empty();
    }

    return Optional.of(methodTestContext.getRequiredTestMethod().getName());
  }

  public ExtensionContext getTestContext() {
    return Optional.ofNullable(methodTestContext).orElse(testContext);
  }

  public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
    return getTestContext().getRequiredTestClass().isAnnotationPresent(annotationClass);
  }

  public Path getLogFolder() {
    return Paths.get(LOG_FILE_PATH);
  }

  public Path getLogFile() {
    return getLogFolder().resolve(getRunningTestClassName() + LOG_SUFFIX);
  }

  public <T extends Annotation> Optional<T> getAnnotatedConfiguration(Class<T> clazz) {
    return getAnnotatedConfiguration(clazz, (s) -> true);
  }

  public <T extends Annotation> Optional<T> getAnnotatedConfiguration(
      Class<T> clazz, Predicate<T> apply) {
    List<Annotation> configurationsByClass = annsForConfiguration.get(clazz);
    if (configurationsByClass == null) {
      configurationsByClass = loadAnnotatedConfiguration(clazz);
      annsForConfiguration.put(clazz, configurationsByClass);
    }

    return configurationsByClass.stream()
        .filter(clazz::isInstance)
        .map(clazz::cast)
        .filter(apply)
        .findFirst();
  }

  public <T extends Annotation, C> C loadCustomConfiguration(
      String target, BaseConfigurationBuilder<T, C> builder) {
    if (customConfigurationByTarget.containsKey(target)) {
      throw new RuntimeException("Target configuration has been already loaded: " + target);
    }

    C configuration = ConfigurationLoader.load(target, this, builder);
    customConfigurationByTarget.put(target, configuration);
    return configuration;
  }

  public void markTestSuiteAsFailed() {
    failed = true;
  }

  private List<Annotation> loadAnnotatedConfiguration(Class<? extends Annotation> clazz) {
    return Arrays.asList(testContext.getRequiredTestClass().getAnnotationsByType(clazz));
  }

  private static String generateContextId(ExtensionContext context) {
    String fullId =
        context.getRequiredTestClass().getSimpleName() + "-" + System.currentTimeMillis();
    return fullId.substring(0, Math.min(COMPONENT_TEST_ID_MAX_SIZE, fullId.length()));
  }
}
