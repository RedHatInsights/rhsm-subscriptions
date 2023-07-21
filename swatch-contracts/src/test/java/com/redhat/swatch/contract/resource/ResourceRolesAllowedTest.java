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
package com.redhat.swatch.contract.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.smallrye.openapi.runtime.OpenApiDocumentService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.io.OpenApiParser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

@QuarkusTest
class ResourceRolesAllowedTest {
  Set<Class<? extends Annotation>> HTTP_METHOD_ANNOTATION_CLASSES =
      Set.of(
          DELETE.class, GET.class, HEAD.class, OPTIONS.class, PATCH.class, POST.class, PUT.class);

  @Inject OpenApiDocumentService openApiDocumentService;

  @TestFactory
  Stream<DynamicTest> testResourceMethodAnnotatedWithAppropriateRoles() throws IOException {
    var yamlDocument = openApiDocumentService.getYamlDocument();
    var api = OpenApiParser.parse(new ByteArrayInputStream(yamlDocument), Format.YAML);
    return findResourceMethods().stream()
        .map(
            method ->
                DynamicTest.dynamicTest(
                    method.getDeclaringClass().getSimpleName() + "#" + method.getName(),
                    () -> {
                      String path = getPath(method);
                      String httpMethod = getHttpMethod(method);
                      assertNotNull(
                          httpMethod, String.format("path %s has no http method defined", path));
                      var pathItem = api.getPaths().getPathItem(path);
                      assertNotNull(
                          pathItem,
                          String.format(
                              "path %s method %s not defined in openapi spec", path, httpMethod));
                      var operation =
                          switch (httpMethod) {
                            case "DELETE" -> pathItem.getDELETE();
                            case "GET" -> pathItem.getGET();
                            case "HEAD" -> pathItem.getHEAD();
                            case "OPTIONS" -> pathItem.getOPTIONS();
                            case "POST" -> pathItem.getPOST();
                            case "PUT" -> pathItem.getPUT();
                            default -> throw new UnsupportedOperationException(httpMethod);
                          };
                      if (!path.startsWith("/api/rhsm-subscriptions/")) {
                        assertNotNull(
                            operation.getSecurity(),
                            String.format(
                                "Security schemes not defined for path %s method %s",
                                path, httpMethod));
                        assertTrue(
                            operation.getSecurity().size() > 0,
                            String.format(
                                "Security schemes must not be empty for path %s method %s",
                                path, httpMethod));
                        for (var requirement : operation.getSecurity()) {
                          assertEquals(
                              1,
                              requirement.getSchemes().size(),
                              "Requirements of multiple schemes not supported");
                        }
                        var allSchemes =
                            operation.getSecurity().stream()
                                .flatMap(req -> req.getSchemes().keySet().stream())
                                .collect(Collectors.toSet());
                        var rolesAllowed =
                            Optional.ofNullable(findAnnotation(RolesAllowed.class, method))
                                .map(RolesAllowed::value)
                                .orElse(new String[] {});
                        assertEquals(allSchemes, new HashSet<>(Arrays.asList(rolesAllowed)));
                      } else {
                        var rolesAllowed =
                            Optional.ofNullable(findAnnotation(RolesAllowed.class, method))
                                .map(RolesAllowed::value)
                                .orElse(new String[] {});
                        assertEquals(
                            Set.of("customer"), new HashSet<>(Arrays.asList(rolesAllowed)));
                      }
                    }));
  }

  private String getHttpMethod(Method method) {
    for (Class<? extends Annotation> annotationClass : HTTP_METHOD_ANNOTATION_CLASSES) {
      var annotation = findAnnotation(annotationClass, method);
      if (annotation != null) {
        return annotationClass.getSimpleName();
      }
    }
    return null;
  }

  String getPath(Method method) {
    var resourcePath = findAnnotation(Path.class, method.getDeclaringClass());
    var methodPath = findAnnotation(Path.class, method);
    assertNotNull(resourcePath);
    if (methodPath == null) {
      return resourcePath.value();
    }
    return String.join("/", resourcePath.value(), methodPath.value()).replace("//", "/");
  }

  List<Method> findResourceMethods() {
    var allBeans = CDI.current().getBeanManager().getBeans(Object.class);
    return allBeans.stream()
        .filter(this::hasJaxRsAnnotation)
        .flatMap(bean -> Arrays.stream(bean.getBeanClass().getDeclaredMethods()))
        .filter(this::hasJaxRsAnnotation)
        .toList();
  }

  private <T extends Annotation> T findAnnotation(Class<T> annotationClass, Method method) {
    var annotation = method.getAnnotation(annotationClass);
    if (annotation != null) {
      return annotation;
    }
    for (Class<?> iface : method.getDeclaringClass().getInterfaces()) {
      try {
        annotation =
            iface
                .getMethod(method.getName(), method.getParameterTypes())
                .getAnnotation(annotationClass);
        if (annotation != null) {
          return annotation;
        }
      } catch (NoSuchMethodException e) {
        // intentionally empty
      }
    }
    return null;
  }

  private <T extends Annotation> T findAnnotation(Class<T> annotationClass, Class<?> clazz) {
    var annotation = clazz.getAnnotation(annotationClass);
    if (annotation != null) {
      return annotation;
    }
    for (Class<?> iface : clazz.getInterfaces()) {
      annotation = iface.getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  private boolean hasJaxRsAnnotation(Method method) {
    for (Class<? extends Annotation> annotation : HTTP_METHOD_ANNOTATION_CLASSES) {
      if (findAnnotation(annotation, method) != null) {
        return true;
      }
    }
    return false;
  }

  private boolean hasJaxRsAnnotation(Class<?> clazz) {
    return findAnnotation(Path.class, clazz) != null;
  }

  private boolean hasJaxRsAnnotation(Bean<?> bean) {
    return hasJaxRsAnnotation(bean.getBeanClass());
  }
}
