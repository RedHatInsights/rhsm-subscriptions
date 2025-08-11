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
import java.util.Optional;
import java.util.stream.Collectors;
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

  private final List<AnnotationBinding> bindingsRegistry =
      ServiceLoaderUtils.load(AnnotationBinding.class);
  private final List<ExtensionBootstrap> extensionsRegistry =
      ServiceLoaderUtils.load(ExtensionBootstrap.class);

  private List<ServiceContext> services = new ArrayList<>();
  private ComponentTestContext context;
  private List<ExtensionBootstrap> extensions;

  @Override
  public void beforeAll(ExtensionContext testContext) {
    // Init context
    context = new ComponentTestContext(testContext);
    Log.configure();
    Log.debug("Context ID: '%s'", context.getId());

    // Init extensions
    extensions = initExtensions();
    extensions.forEach(ext -> ext.beforeAll(context));

    // Init services from class annotations
    ReflectionUtils.findAllAnnotations(testContext.getRequiredTestClass())
        .forEach(annotation -> initServiceFromAnnotation(annotation));

    // Init services from static fields
    ReflectionUtils.findAllFields(testContext.getRequiredTestClass()).stream()
        .filter(ReflectionUtils::isStatic)
        .forEach(field -> initResourceFromField(testContext, field));
  }

  @Override
  public void afterAll(ExtensionContext testContext) {
    try {
      List<ServiceContext> servicesToFinish = new ArrayList<>(services);
      Collections.reverse(servicesToFinish);
      servicesToFinish.forEach(s -> s.getOwner().stop());
      deleteLogIfTestSuitePassed();
      services.clear();
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
    services.forEach(
        service -> {
          if (!service.getOwner().isRunning()) {
            service.getOwner().start();
          }

          service.getOwner().onTestStarted();
        });
  }

  @Override
  public void afterEach(ExtensionContext testContext) {
    services.forEach(service -> service.getOwner().onTestStarted());

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
    Log.debug(service, "Initialize service (%s)", service.getDisplayName());
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

  private void initResourceFromField(ExtensionContext context, Field field) {
    if (Service.class.isAssignableFrom(field.getType())) {
      Service service = ReflectionUtils.getFieldValue(findTestInstance(context, field), field);
      initService(service, field.getName(), field.getAnnotations());
    } else if (InjectUtils.isAnnotatedWithInject(field)) {
      injectDependency(context, field);
    }
  }

  private void initServiceFromAnnotation(Annotation annotation) {
    getAnnotationBinding(annotation)
        .ifPresent(
            binding ->
                initService(
                    binding.getDefaultServiceImplementation(),
                    binding.getDefaultName(annotation),
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

  private void initService(Service service, String name, Annotation... annotations) {
    AnnotationBinding binding =
        getAnnotationBinding(annotations)
            .orElseThrow(() -> new RuntimeException("Unknown annotation for service"));
    initService(service, name, binding, annotations);
  }

  private void initService(
      Service service, String name, AnnotationBinding binding, Annotation... annotations) {
    if (service.isRunning()) {
      return;
    }

    // Validate
    service.validate(binding, annotations);

    // Resolve managed resource
    ManagedResource resource = getManagedResource(name, service, binding, annotations);

    // Initialize it
    ServiceContext serviceContext = service.register(name, context);
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
