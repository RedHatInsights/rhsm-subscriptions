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

import static org.junit.jupiter.api.Assertions.fail;

import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.api.ServiceLifecycle;
import com.redhat.swatch.component.tests.api.extensions.AnnotationBinding;
import com.redhat.swatch.component.tests.api.extensions.ExtensionBootstrap;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.InjectUtils;
import com.redhat.swatch.component.tests.utils.ReflectionUtils;
import com.redhat.swatch.component.tests.utils.ServiceLoaderUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.jupiter.api.extension.TestWatcher;

public class ComponentTestExtension
    implements BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        ParameterResolver,
        LifecycleMethodExecutionExceptionHandler,
        TestWatcher {

  // Store namespace and key for suite-level services
  private static final ExtensionContext.Namespace SUITE_NAMESPACE =
      ExtensionContext.Namespace.create("swatch", "component-tests", "suite");
  private static final String SUITE_SERVICES_KEY = "suite-services";

  private final List<AnnotationBinding> bindingsRegistry =
      ServiceLoaderUtils.load(AnnotationBinding.class);
  private final List<ExtensionBootstrap> extensionsRegistry =
      ServiceLoaderUtils.load(ExtensionBootstrap.class);

  private List<ServiceContext> services = new ArrayList<>(); // Services started by this test class
  private List<ServiceContext> reusedServices =
      new ArrayList<>(); // Services reused from suite store
  private ComponentTestContext context;
  private List<ExtensionBootstrap> extensions;

  @Override
  public void beforeAll(ExtensionContext testContext) {
    // Init context
    context = new ComponentTestContext(testContext);
    Log.configure();
    Log.debug("Context ID: '%s'", context.getId());

    // Get suite-level store for TEST_SUITE scoped services
    ExtensionContext.Store suiteStore = testContext.getRoot().getStore(SUITE_NAMESPACE);

    // Init extensions
    extensions = initExtensions();
    extensions.forEach(ext -> ext.beforeAll(context));

    // Init services from class annotations
    ReflectionUtils.findAllAnnotations(testContext.getRequiredTestClass())
        .forEach(annotation -> initServiceFromAnnotation(annotation, suiteStore));

    // Init services from static fields
    ReflectionUtils.findAllFields(testContext.getRequiredTestClass()).stream()
        .filter(ReflectionUtils::isStatic)
        .forEach(field -> initResourceFromField(testContext, field, suiteStore));
  }

  @Override
  public void afterAll(ExtensionContext testContext) {
    try {
      // Only stop TEST_CLASS scoped services
      // TEST_SUITE services remain running until JVM shutdown
      List<ServiceContext> servicesToStop =
          services.stream()
              .filter(s -> s.getLifecycle() == ServiceLifecycle.TEST_CLASS)
              .collect(Collectors.toList());

      Collections.reverse(servicesToStop);
      servicesToStop.forEach(s -> s.getOwner().stop());

      deleteLogIfTestSuitePassed();
      services.clear();
      reusedServices.clear(); // Clear reused services list too
    } finally {
      extensions.forEach(ext -> ext.afterAll(context));
    }
  }

  @Override
  public void beforeEach(ExtensionContext testContext) {
    // Init services from instance fields
    ReflectionUtils.findAllFields(testContext.getRequiredTestClass()).stream()
        .filter(ReflectionUtils::isInstance)
        .forEach(field -> initResourceFromField(testContext, field));

    Log.info(
        "## Running test "
            + testContext.getParent().map(ctx -> ctx.getDisplayName() + ".").orElse("")
            + testContext.getDisplayName());
    context.setMethodTestContext(testContext);
    extensions.forEach(ext -> ext.beforeEach(context));

    // Handle both services started by this class AND reused services from suite
    Stream.concat(services.stream(), reusedServices.stream())
        .forEach(
            service -> {
              if (!service.getOwner().isRunning()) {
                service.getOwner().start();
              }

              service.getOwner().onTestStarted();
            });
  }

  @Override
  public void afterEach(ExtensionContext testContext) {
    // Notify both our services and reused services that test stopped
    Stream.concat(services.stream(), reusedServices.stream())
        .forEach(service -> service.getOwner().onTestStopped());

    if (!isClassLifecycle(testContext)) {
      // Stop services from instance fields
      ReflectionUtils.findAllFields(testContext.getRequiredTestClass()).stream()
          .filter(ReflectionUtils::isInstance)
          .forEach(field -> stopServiceFromField(testContext, field));
    }

    extensions.forEach(ext -> ext.afterEach(context));
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    return isParameterSupported(parameterContext.getParameter().getType());
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    return getParameter(new DependencyContext(parameterContext));
  }

  @Override
  public void handleAfterAllMethodExecutionException(
      ExtensionContext context, Throwable throwable) {
    testOnError(throwable);
  }

  @Override
  public void handleAfterEachMethodExecutionException(
      ExtensionContext context, Throwable throwable) {
    testOnError(throwable);
  }

  @Override
  public void handleBeforeAllMethodExecutionException(
      ExtensionContext context, Throwable throwable) {
    testOnError(throwable);
  }

  @Override
  public void testSuccessful(ExtensionContext testContext) {
    extensions.forEach(ext -> ext.onSuccess(context));
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    testOnError(cause);
  }

  @Override
  public void testDisabled(ExtensionContext testContext, Optional<String> reason) {
    extensions.forEach(ext -> ext.onDisabled(context, reason));
  }

  @Override
  public void handleBeforeEachMethodExecutionException(
      ExtensionContext context, Throwable throwable) {
    testOnError(throwable);
  }

  private void launchService(Service service) {
    Log.info(service, "Starting service (%s) ...", service.getDisplayName());
    extensions.forEach(ext -> ext.onServiceLaunch(context, service));
    try {
      service.start();
    } catch (Throwable throwable) {
      testOnError(throwable);
      throw throwable;
    }
  }

  private void testOnError(Throwable throwable) {
    // mark test suite as failed
    context.markTestSuiteAsFailed();
    // notify extensions
    extensions.forEach(ext -> ext.onError(context, throwable));
  }

  // Backward compatible version for instance fields (no store needed)
  private void initResourceFromField(ExtensionContext context, Field field) {
    initResourceFromField(context, field, null);
  }

  // New version with store parameter for static fields
  private void initResourceFromField(
      ExtensionContext context, Field field, ExtensionContext.Store suiteStore) {
    if (Service.class.isAssignableFrom(field.getType())) {
      Service service = ReflectionUtils.getFieldValue(findTestInstance(context, field), field);
      Annotation[] annotations = field.getAnnotations();
      if (annotations.length > 0) {
        getAnnotationBinding(annotations[0])
            .ifPresent(
                binding -> initService(service, field.getName(), suiteStore, binding, annotations));
      }
    } else if (InjectUtils.isAnnotatedWithInject(field)) {
      injectDependency(context, field);
    }
  }

  private void initServiceFromAnnotation(Annotation annotation, ExtensionContext.Store suiteStore) {
    getAnnotationBinding(annotation)
        .ifPresent(
            binding ->
                initService(
                    binding.getDefaultServiceImplementation(),
                    binding.getDefaultName(annotation),
                    suiteStore,
                    binding,
                    annotation));
  }

  private void stopServiceFromField(ExtensionContext context, Field field) {
    if (Service.class.isAssignableFrom(field.getType())) {
      Service service = ReflectionUtils.getFieldValue(findTestInstance(context, field), field);
      service.stop();
      services.removeIf(s -> service.getName().equals(s.getName()));
    }
  }

  private void injectDependency(ExtensionContext testContext, Field field) {
    Object fieldValue = null;
    if (ComponentTestContext.class.equals(field.getType())) {
      fieldValue = context;
    } else if (isParameterSupported(field.getType())) {
      fieldValue =
          getParameter(
              new DependencyContext(field.getName(), field.getType(), field.getAnnotations()));
    }

    if (fieldValue != null) {
      ReflectionUtils.setFieldValue(findTestInstance(testContext, field), field, fieldValue);
    }
  }

  // Backward compatible version (no store = TEST_CLASS lifecycle)
  private void initService(Service service, String name, Annotation... annotations) {
    AnnotationBinding binding =
        getAnnotationBinding(annotations)
            .orElseThrow(() -> new RuntimeException("Unknown annotation for service"));
    initService(service, name, null, binding, annotations);
  }

  // New version with store and lifecycle logic
  private void initService(
      Service service,
      String name,
      ExtensionContext.Store suiteStore,
      AnnotationBinding binding,
      Annotation... annotations) {

    // Get lifecycle from annotation
    ServiceLifecycle lifecycle = binding.getLifecycle(annotations[0]);

    switch (lifecycle) {
      case TEST_SUITE:
        // Suite-scoped services: check if already started
        if (suiteStore != null) {
          @SuppressWarnings("unchecked")
          Map<String, ServiceContext> suiteServices =
              suiteStore.getOrComputeIfAbsent(
                  SUITE_SERVICES_KEY,
                  key -> new ConcurrentHashMap<String, ServiceContext>(),
                  Map.class);

          // Check if service already exists in suite
          if (suiteServices.containsKey(name)) {
            // Reuse existing service - add to reusedServices list (not services list)
            ServiceContext existingContext = suiteServices.get(name);
            reusedServices.add(existingContext);
            Log.info("Reusing %s scoped service: %s", lifecycle, name);
            return;
          }

          // Service not in suite yet - start it
          if (service.isRunning()) {
            return;
          }

          // Validate
          service.validate(binding, annotations);

          // Resolve managed resource
          ManagedResource resource = getManagedResource(name, service, binding, annotations);

          // Initialize with lifecycle
          ServiceContext serviceContext = service.register(name, context, lifecycle);

          // Create output directory for service logs
          serviceContext.getServiceFolder().toFile().mkdirs();

          service.init(resource);

          // Store in suite registry (don't add to local services list - it's suite-scoped!)
          suiteServices.put(name, serviceContext);

          // Add to reusedServices list so beforeEach/afterEach can notify it
          reusedServices.add(serviceContext);

          // Register cleanup callback for automatic shutdown
          suiteStore.put(name + "-cleanup", new ServiceCleanupCallback(service, name));

          Log.info("Starting %s scoped service: %s (will run until JVM shutdown)", lifecycle, name);
          extensions.forEach(ext -> ext.updateServiceContext(serviceContext));
          launchService(service);
        } else {
          // No store provided (shouldn't happen for static fields, but handle gracefully)
          Log.warn(
              "No suite store provided for %s scoped service %s, falling back to TEST_CLASS behavior",
              lifecycle, name);
          initServiceAsTestClass(service, name, binding, annotations);
        }
        break;

      case TEST_CLASS:
        // Original per-class behavior
        initServiceAsTestClass(service, name, binding, annotations);
        break;

      default:
        throw new IllegalArgumentException("Unknown lifecycle: " + lifecycle);
    }
  }

  private void initServiceAsTestClass(
      Service service, String name, AnnotationBinding binding, Annotation... annotations) {
    if (service.isRunning()) {
      return;
    }

    // Validate
    service.validate(binding, annotations);

    // Resolve managed resource
    ManagedResource resource = getManagedResource(name, service, binding, annotations);

    // Initialize with TEST_CLASS lifecycle
    ServiceContext serviceContext = service.register(name, context, ServiceLifecycle.TEST_CLASS);
    service.init(resource);
    services.add(serviceContext);

    extensions.forEach(ext -> ext.updateServiceContext(serviceContext));
    launchService(service);
  }

  private Optional<AnnotationBinding> getAnnotationBinding(Annotation... annotations) {
    return bindingsRegistry.stream().filter(b -> b.isFor(annotations)).findFirst();
  }

  private ManagedResource getManagedResource(
      String name, Service service, AnnotationBinding binding, Annotation... annotations) {
    try {
      return binding.getManagedResource(context, service, annotations);
    } catch (Exception ex) {
      throw new RuntimeException("Could not create the Managed Resource for " + name, ex);
    }
  }

  private boolean isParameterSupported(Class<?> paramType) {
    return paramType.isAssignableFrom(ComponentTestContext.class)
        || extensions.stream().anyMatch(ext -> ext.supportedParameters().contains(paramType));
  }

  private Object getParameter(DependencyContext dependency) {
    if (dependency.getType().isAssignableFrom(ComponentTestContext.class)) {
      return context;
    }

    Optional<Object> parameter =
        extensions.stream()
            .map(ext -> ext.getParameter(dependency))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

    if (parameter.isEmpty()) {
      fail("Failed to inject: " + dependency.getName());
    }

    return parameter.get();
  }

  private List<ExtensionBootstrap> initExtensions() {
    return extensionsRegistry.stream()
        .filter(binding -> binding.appliesFor(context))
        .map(
            binding -> {
              binding.updateContext(context);
              return binding;
            })
        .collect(Collectors.toList());
  }

  private void deleteLogIfTestSuitePassed() {
    if (!context.isFailed()) {
      context.getLogFile().toFile().delete();
    }
  }

  private boolean isClassLifecycle(ExtensionContext context) {
    if (context.getTestInstanceLifecycle().isPresent()) {
      return context.getTestInstanceLifecycle().get() == TestInstance.Lifecycle.PER_CLASS;
    } else if (context.getParent().isPresent()) {
      return isClassLifecycle(context.getParent().get());
    }

    return false;
  }

  private Optional<Object> findTestInstance(ExtensionContext context, Field field) {
    Optional<TestInstances> testInstances = context.getTestInstances();
    if (testInstances.isPresent()) {
      return testInstances.get().findInstance((Class<Object>) field.getDeclaringClass());
    }

    return context.getTestInstance();
  }
}
