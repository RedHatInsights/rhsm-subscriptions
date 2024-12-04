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
package com.redhat.swatch.configuration.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.yaml.snakeyaml.composer.ComposerException;

@TestInstance(Lifecycle.PER_CLASS)
class SubscriptionDefinitionRegistryTest {

  SubscriptionDefinitionRegistry subscriptionDefinitionRegistry;

  @BeforeAll
  void setup() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    var url = classLoader.getResource("swatch_config_index.txt");

    if (Files.isSymbolicLink(Paths.get(url.getPath()))) {
      var warning =
          """
      Detected a symlink version of swatch_config_index.txt.  This link should only be used
      in testNoGlobalTags().  Its presence indicates a failure of the test to clean up after itself.
      Please delete the symlink at %s and rerun the tests.
      Also consider investigating why the symlink was not deleted after completion of the test.
      """
              .formatted(url.getPath());
      throw new IllegalStateException(warning);
    }

    subscriptionDefinitionRegistry = new SubscriptionDefinitionRegistry();
  }

  @Test
  void testValidations() {
    try (ValidatorFactory factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      var validator = factory.getValidator();
      for (var definition : subscriptionDefinitionRegistry.getSubscriptions()) {
        var violations = validator.validate(definition);
        assertTrue(
            violations.isEmpty(),
            "Found the following violations in " + definition + ":" + violations);
      }
    }
  }

  /**
   * Test for SnakeYaml deserialization vulnerability due to enabled global tags. See
   *
   * <ul>
   *   <li>https://www.cve.org/CVERecord?id=CVE-2022-1471
   *   <li>https://www.veracode.com/blog/research/resolving-cve-2022-1471-snakeyaml-20-release-0
   *   <li>https://bitbucket.org/snakeyaml/snakeyaml/issues/561/cve-2022-1471-vulnerability-in
   *   <li>https://www.websec.ca/publication/Blog/CVE-2022-21404-Another-story-of-developers-fixing-vulnerabilities-unknowingly-because-of-CodeQL
   * </ul>
   */
  @Test
  void testNoGlobalTags() throws IOException {
    Path link = null;
    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      var url = classLoader.getResource("cve_2022_1471_swatch_config_index.txt");
      Path target = Paths.get(url.getPath());
      Path parent = target.getParent();

      link = parent.resolve("swatch_config_index.txt");
      Files.createSymbolicLink(link, target);
      assertThrows(ComposerException.class, SubscriptionDefinitionRegistry::new);
    } finally {
      if (link != null && Files.exists(link)) {
        Files.delete(link);
      }
    }
  }

  @Test
  void testLoadAllTheThings() {
    assertFalse(subscriptionDefinitionRegistry.getSubscriptions().isEmpty());
  }
}
