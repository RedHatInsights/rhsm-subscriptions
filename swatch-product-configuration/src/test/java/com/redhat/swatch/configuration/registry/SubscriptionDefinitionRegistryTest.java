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

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class SubscriptionDefinitionRegistryTest {

  SubscriptionDefinitionRegistry subscriptionDefinitionRegistry;

  @BeforeAll
  void setup() {
    subscriptionDefinitionRegistry = new SubscriptionDefinitionRegistry();
  }

  @Test
  void testValidations() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (var definition : subscriptionDefinitionRegistry.getSubscriptions()) {
        var violations = validator.validate(definition);
        assertTrue(
            violations.isEmpty(),
            "Found the following violations in " + definition + ":" + violations);
      }
    }
  }

  @Test
  void testLoadAllTheThings() {
    assertFalse(subscriptionDefinitionRegistry.getSubscriptions().isEmpty());
  }
}
