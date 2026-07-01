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

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.reporting.Property;
import com.redhat.swatch.component.tests.reporting.PropertyExtractor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Extracts properties from JUnit {@link com.redhat.swatch.component.tests.api.ComponentTest}
 * annotations.
 */
public class ComponentPropertyExtractor implements PropertyExtractor<ComponentTest> {

  private static final String KEY = "component";

  @Override
  public Class<ComponentTest> getAnnotation() {
    return ComponentTest.class;
  }

  @Override
  public Set<Property> extractProperties(
      TestIdentifier testIdentifier, List<ComponentTest> annotations) {
    return annotations.stream()
        .filter(a -> !a.name().isEmpty())
        .map(a -> new Property(KEY, a.name()))
        .collect(Collectors.toSet());
  }
}
