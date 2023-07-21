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
package org.candlepin.subscriptions.validator;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.annotation.Validated;

@SpringBootTest
@ActiveProfiles("test")
class UuidValidatorTest {

  @Autowired ValidatedThing validatedThing;

  @TestConfiguration
  static class UuidValidatorTestConfig {
    @Bean
    ValidatedThing validatedThing() {
      return new ValidatedThing();
    }
  }

  @Validated
  static class ValidatedThing {

    public boolean mySingleMethod(@Uuid String uuid) {
      return true;
    }

    public boolean myCollectionMethod(List<@Uuid String> uuids) {
      return true;
    }
  }

  @Test
  void testValidUuid() {
    assertTrue(validatedThing.mySingleMethod("b682126b-88d8-40b7-ada6-212f0e65e30a"));
    assertTrue(validatedThing.mySingleMethod("B682126B-88D8-40B7-ADA6-212F0E65E30A"));
    assertTrue(
        validatedThing.myCollectionMethod(
            List.of(
                "7ac9da96-d11d-44ca-b7dc-7480eab5e750",
                "6c0c79d2-d1d4-4f2a-85e0-7631b5a58f7e",
                "f909b5a8-a8de-4721-950d-5f48be84ee21",
                "5922f04f-8398-49b7-a9dd-c2d04a9b7d16")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "hellowor-ldhe-llow-worl-dhelloworldh",
        "hellow&!-ld%%-llow-worl-dhellow*rldh",
        "6c0c79d2-d1d4-4f2a-85e0-7631b5-a58f7e", // Valid but not in the 8-4-4-4-12 format
        "190464649652423269416550308090595239059", // This is a UUID in decimal format
        "b682126b88d840b7ada6212f0e65e30a",
        "b682126b88d840b7ada6212f",
        "192.168.2.8.4",
        "redhat.com"
      })
  void testInvalidUuid(String s) {
    var ex =
        assertThrows(ConstraintViolationException.class, () -> validatedThing.mySingleMethod(s));
    Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
    assertEquals(1, constraintViolations.size());
    var violation = constraintViolations.stream().findFirst().get();
    String expected = String.format("%s is not a valid UUID", s);
    assertEquals(expected, violation.getMessage());
  }

  @Test
  void testEmptyStringAndNull() {
    String expected = "Must be a valid UUID";
    var ex =
        assertThrows(ConstraintViolationException.class, () -> validatedThing.mySingleMethod(""));
    Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
    assertEquals(1, constraintViolations.size());
    var violation = constraintViolations.stream().findFirst().get();
    assertEquals(expected, violation.getMessage());

    ex =
        assertThrows(ConstraintViolationException.class, () -> validatedThing.mySingleMethod(null));
    constraintViolations = ex.getConstraintViolations();
    assertEquals(1, constraintViolations.size());
    violation = constraintViolations.stream().findFirst().get();
    assertEquals(expected, violation.getMessage());
  }
}
