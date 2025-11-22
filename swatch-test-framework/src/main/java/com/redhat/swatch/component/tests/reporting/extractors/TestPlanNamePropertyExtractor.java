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
package com.redhat.swatch.component.tests.reporting.extractors;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.reporting.Property;
import com.redhat.swatch.component.tests.reporting.PropertyExtractor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.platform.launcher.TestIdentifier;

/** Extracts properties from JUnit {@link TestPlanName} annotations. */
public class TestPlanNamePropertyExtractor implements PropertyExtractor<TestPlanName> {

  private static final String KEY = "test-plan";

  @Override
  public Class<TestPlanName> getAnnotation() {
    return TestPlanName.class;
  }

  @Override
  public Set<Property> extractProperties(
      TestIdentifier testIdentifier, List<TestPlanName> annotations) {
    return annotations.stream().map(a -> new Property(KEY, a.value())).collect(Collectors.toSet());
  }
}
