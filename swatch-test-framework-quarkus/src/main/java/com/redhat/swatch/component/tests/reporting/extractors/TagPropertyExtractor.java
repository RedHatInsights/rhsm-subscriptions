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

import com.redhat.swatch.component.tests.reporting.Property;
import com.redhat.swatch.component.tests.reporting.PropertyExtractor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.platform.engine.TestTag;
import org.junit.platform.launcher.TestIdentifier;

/** Extracts properties from JUnit {@link Tag} annotations. */
public class TagPropertyExtractor implements PropertyExtractor<Tag> {

  private static final String KEY = "tag";

  @Override
  public Class<Tag> getAnnotation() {
    return Tag.class;
  }

  @Override
  public Set<Property> extractProperties(TestIdentifier testIdentifier, List<Tag> annotations) {
    Set<Property> properties = new HashSet<>();

    // Add tags from JUnit Platform (includes method and class level tags)
    for (TestTag tag : testIdentifier.getTags()) {
      properties.add(new Property(KEY, tag.getName()));
    }

    // Additionally, collect tags from class hierarchy
    annotations.forEach(a -> properties.add(new Property(KEY, a.value())));
    return properties;
  }
}
