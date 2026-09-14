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
package com.redhat.swatch.component.tests.reporting;

import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.KEY;
import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.NAME;
import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.PROPERTIES;
import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.PROPERTY;
import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.VALUE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class JUnitXmlMetadata {

  private static final List<String> RUN_PROPERTY_NAMES =
      List.of(
          "component",
          "git_revision",
          "pipeline_name",
          "event_type",
          "integration_test_name",
          "env",
          "tool",
          "source");

  private JUnitXmlMetadata() {}

  public static Map<String, String> runMetadataFromSystemProperties() {
    Map<String, String> metadata = new LinkedHashMap<>();
    for (String name : RUN_PROPERTY_NAMES) {
      String value = System.getProperty(name);
      if (value != null && !value.isBlank()) {
        metadata.put(name, value);
      }
    }

    return metadata;
  }

  public static void upsertRunMetadataOnElement(Element root, Map<String, String> metadata) {
    if (metadata.isEmpty()) {
      return;
    }

    Document doc = root.getOwnerDocument();
    Element properties = findOrCreatePropertiesElement(doc, root);
    Map<String, Element> existing = new LinkedHashMap<>();
    NodeList propertyNodes = properties.getElementsByTagName(PROPERTY);
    for (int i = 0; i < propertyNodes.getLength(); i++) {
      Element prop = (Element) propertyNodes.item(i);
      String key = prop.getAttribute(KEY);
      if (key.isBlank()) {
        key = prop.getAttribute(NAME);
      }
      if (!key.isBlank()) {
        existing.put(key, prop);
      }
    }
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      Element prop = existing.get(entry.getKey());
      if (prop == null) {
        prop = doc.createElement(PROPERTY);
        properties.appendChild(prop);
      }
      setPropertyAttributes(prop, entry.getKey(), entry.getValue());
    }
  }

  public static void appendProperty(
      Document doc, Element propertiesElement, String name, String value) {
    Element property = doc.createElement(PROPERTY);
    setPropertyAttributes(property, name, value);
    propertiesElement.appendChild(property);
  }

  public static Element findOrCreatePropertiesElement(Document doc, Element parent) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element child && PROPERTIES.equals(child.getNodeName())) {
        return child;
      }
    }
    Element properties = doc.createElement(PROPERTIES);
    if (parent.hasChildNodes()) {
      parent.insertBefore(properties, parent.getFirstChild());
    } else {
      parent.appendChild(properties);
    }
    return properties;
  }

  private static void setPropertyAttributes(Element property, String name, String value) {
    property.setAttribute(NAME, name);
    property.setAttribute(KEY, name);
    property.setAttribute(VALUE, value);
  }
}
