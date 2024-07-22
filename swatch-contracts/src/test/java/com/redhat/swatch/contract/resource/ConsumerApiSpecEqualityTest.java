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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.redhat.swatch.contract.PathUtils;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.io.OpenApiParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test to verify equality between customer API spec and the service's own copy of the definition.
 *
 * <p>Note that eclipse microprofile OpenAPI does not implement equals methods for its objects;
 * instead we use YAML serialization to make comparison easier. This allows easy diffing when the
 * test fails.
 */
class ConsumerApiSpecEqualityTest {
  private static final OpenAPI monolithSpec =
      loadOpenApiDefinition("../api/rhsm-subscriptions-api-v1-spec.yaml");
  private static final OpenAPI serviceSpec =
      loadOpenApiDefinition("src/main/resources/META-INF/openapi.yaml");
  private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

  private static OpenAPI loadOpenApiDefinition(String path) {
    try {
      return OpenApiParser.parse(
          Files.newInputStream(Paths.get(PathUtils.PROJECT_DIRECTORY, path)), Format.YAML);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest
  @MethodSource(value = "getSchemaNames")
  void testSchemasAreEquivalentToMonolithSpec(String schemaName) throws IOException {
    var monolithSchema = monolithSpec.getComponents().getSchemas().get(schemaName);
    var serviceSchema = serviceSpec.getComponents().getSchemas().get(schemaName);
    assertEquals(
        objectMapper.writeValueAsString(monolithSchema),
        objectMapper.writeValueAsString(serviceSchema));
  }

  private static Stream<String> getSchemaNames() {
    return serviceSpec.getComponents().getSchemas().keySet().stream()
        .filter(name -> monolithSpec.getComponents().getSchemas().containsKey(name))
        // CapacityReportByMetricId uses now a common API model. This can be removed in SWATCH-2733
        .filter(name -> !"CapacityReportByMetricId".equals(name));
  }
}
