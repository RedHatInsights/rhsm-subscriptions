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
package org.candlepin.subscriptions.conduit.inventory;

import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConduitFactsTest {
  @Autowired private Validator validator;

  @Test
  void testFactValidation() {
    ConduitFacts facts = new ConduitFacts();
    facts.setFqdn("");
    facts.setIpAddresses(
        Arrays.asList("192.168.2.1", "127.1", "::1", "1.1.1.", "1200::AB00:1234::2552:7777:1313"));

    Set<ConstraintViolation<ConduitFacts>> violations = validator.validate(facts);
    assertThat(
        getFailingFields(violations),
        Matchers.hasItems(
            Matchers.startsWith("fqdn"),
            Matchers.startsWith("ipAddresses[3]"),
            Matchers.startsWith("ipAddresses[4]")));
  }

  private List<String> getFailingFields(Set<ConstraintViolation<ConduitFacts>> violations) {
    return violations.stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Path::toString)
        .collect(Collectors.toList());
  }
}
