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

import com.redhat.swatch.component.tests.reporting.extractors.ComponentPropertyExtractor;
import com.redhat.swatch.component.tests.reporting.extractors.TagPropertyExtractor;
import com.redhat.swatch.component.tests.reporting.extractors.TestPlanNamePropertyExtractor;
import com.redhat.swatch.component.tests.utils.ReflectionUtils;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Custom test reporter that post-processes Surefire XML reports to add custom properties to each
 * test case. This preserves all the original Surefire data (system-out, system-err, failures,
 * timing, etc.) while adding custom metadata.
 *
 * <p>Properties are extracted using registered {@link PropertyExtractor} implementations. By
 * default, the {@link TagPropertyExtractor} is registered to extract properties from JUnit {@link
 * org.junit.jupiter.api.Tag} annotations.
 *
 * <p>To add support for custom annotations, create a new {@link PropertyExtractor} implementation
 * and add it to the {@link #propertyExtractors} list.
 */
public class ComponentTestReporter implements TestExecutionListener {

  private final Map<String, TestMetadata> testMetadata = new HashMap<>();
  private final String outputDirectory;
  private final List<PropertyExtractor> propertyExtractors = new ArrayList<>();
  private TestPlan testPlan;

  public ComponentTestReporter() {
    // Get the surefire reports directory from system property or use default
    this.outputDirectory =
        System.getProperty("surefire.reports.directory", "target/surefire-reports");

    // Register default property extractors
    propertyExtractors.add(new TagPropertyExtractor());
    propertyExtractors.add(new ComponentPropertyExtractor());
    propertyExtractors.add(new TestPlanNamePropertyExtractor());
  }

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    this.testPlan = testPlan;
  }

  @Override
  public void executionFinished(
      TestIdentifier testIdentifier, org.junit.platform.engine.TestExecutionResult testResult) {
    if (testIdentifier.isTest()) {
      TestMetadata metadata = new TestMetadata();
      metadata.testIdentifier = testIdentifier;
      metadata.properties = extractAllProperties(testIdentifier);

      testMetadata.put(testIdentifier.getUniqueId(), metadata);
    }
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    enhanceSurefireReports();
  }

  /**
   * Extracts all properties from a test identifier using all registered property extractors.
   *
   * @param testIdentifier the test identifier to extract properties from
   * @return a set of property names to their values
   */
  @SuppressWarnings("unchecked")
  private Set<Property> extractAllProperties(TestIdentifier testIdentifier) {
    Set<Property> allProperties = new HashSet<>();

    // Apply all registered property extractors
    for (PropertyExtractor extractor : propertyExtractors) {
      var annotations = getAllAnnotationsByType(testIdentifier, extractor.getAnnotation());
      Set<Property> properties = extractor.extractProperties(testIdentifier, annotations);
      allProperties.addAll(properties);
    }

    return allProperties;
  }

  private <T extends Annotation> List<T> getAllAnnotationsByType(
      TestIdentifier testIdentifier, Class<T> annotation) {
    List<T> annotations = new ArrayList<>();
    // collect annotations from method and class hierarchy using ReflectionUtils
    Optional<TestSource> testSource = testIdentifier.getSource();
    if (testSource.isPresent() && testSource.get() instanceof MethodSource methodSource) {
      try {
        Class<?> testClass = Class.forName(methodSource.getClassName());
        String methodName = methodSource.getMethodName();

        // Find the test method and collect all annotations from method and class hierarchy
        ReflectionUtils.findAllAnnotations(testClass, methodName).stream()
            .filter(annotation::isInstance)
            .map(annotation::cast)
            .forEach(annotations::add);
      } catch (ClassNotFoundException e) {
        // Silently ignore if we can't find the test class
      }
    }

    return annotations;
  }

  /**
   * Enhances Surefire XML reports by adding custom properties. This method waits for Surefire to
   * finish writing the XML files by polling for their existence and stability.
   */
  private void enhanceSurefireReports() {
    File outputDir = new File(outputDirectory);

    // Wait for the output directory to exist
    if (!waitForDirectory(outputDir)) {
      return;
    }

    // Wait for Surefire XML files to be written and stabilized
    File[] xmlFiles = waitForSurefireXmlFiles(outputDir);
    if (xmlFiles == null || xmlFiles.length == 0) {
      return;
    }

    // Process each XML file
    for (File xmlFile : xmlFiles) {
      try {
        enhanceSurefireXml(xmlFile);
      } catch (Exception e) {
        System.err.println(
            "Failed to enhance test report " + xmlFile.getName() + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /** Waits for the output directory to exist, with a timeout. */
  private boolean waitForDirectory(File directory) {
    int maxAttempts = 50; // 5 seconds total
    int attemptDelayMs = 100;

    for (int i = 0; i < maxAttempts; i++) {
      if (directory.exists() && directory.isDirectory()) {
        return true;
      }
      try {
        Thread.sleep(attemptDelayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  /**
   * Waits for Surefire XML files to be written and stabilized. A file is considered stable when its
   * size doesn't change between checks.
   */
  private File[] waitForSurefireXmlFiles(File outputDir) {
    int maxAttempts = 50; // 5 seconds total
    int attemptDelayMs = 100;
    Map<String, Long> previousSizes = new HashMap<>();

    for (int i = 0; i < maxAttempts; i++) {
      File[] xmlFiles =
          outputDir.listFiles((dir, name) -> name.startsWith("TEST-") && name.endsWith(".xml"));

      if (xmlFiles != null && xmlFiles.length > 0) {
        // Check if all files are stable (size hasn't changed)
        boolean allStable = true;
        Map<String, Long> currentSizes = new HashMap<>();

        for (File xmlFile : xmlFiles) {
          long currentSize = xmlFile.length();
          currentSizes.put(xmlFile.getName(), currentSize);

          Long previousSize = previousSizes.get(xmlFile.getName());
          if (previousSize == null || previousSize != currentSize) {
            allStable = false;
          }
        }

        if (allStable && !previousSizes.isEmpty()) {
          // All files are stable, return them
          return xmlFiles;
        }

        previousSizes = currentSizes;
      }

      try {
        Thread.sleep(attemptDelayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }

    // Timeout reached, return whatever files we have
    return outputDir.listFiles((dir, name) -> name.startsWith("TEST-") && name.endsWith(".xml"));
  }

  private void enhanceSurefireXml(File xmlFile) throws Exception {
    // Parse the existing Surefire XML
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(xmlFile);

    // Remove default Surefire properties from testsuite element
    removeTestsuiteProperties(doc);

    // Find all testcase elements
    NodeList testcases = doc.getElementsByTagName("testcase");
    boolean modified = false;

    for (int i = 0; i < testcases.getLength(); i++) {
      Element testcase = (Element) testcases.item(i);
      String testName = testcase.getAttribute("name");
      String className = testcase.getAttribute("classname");

      // Find matching metadata
      TestMetadata metadata = findMetadataForTest(className, testName);
      if (metadata != null && !metadata.properties.isEmpty()) {
        // Check if properties element already exists
        Element propertiesElement = findOrCreatePropertiesElement(doc, testcase);

        // Add all properties
        for (Property entry : metadata.properties) {
          Element property = doc.createElement("property");
          property.setAttribute("name", entry.name());
          property.setAttribute("value", entry.value());
          propertiesElement.appendChild(property);
        }

        modified = true;
      }
    }

    // Save the modified XML back to the file if any changes were made
    if (modified) {
      // Write the modified XML
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(xmlFile);
      transformer.transform(source, result);
    }
  }

  /**
   * Removes the default Surefire properties from the testsuite element. These properties contain
   * system information (java.version, os.name, etc.) that we don't want to send to ReportPortal.
   */
  private void removeTestsuiteProperties(Document doc) {
    NodeList testsuites = doc.getElementsByTagName("testsuite");
    for (int i = 0; i < testsuites.getLength(); i++) {
      Element testsuite = (Element) testsuites.item(i);
      NodeList children = testsuite.getChildNodes();

      // Find and remove properties element
      for (int j = 0; j < children.getLength(); j++) {
        Node child = children.item(j);
        if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals("properties")) {
          testsuite.removeChild(child);
          break; // Only one properties element per testsuite
        }
      }
    }
  }

  private Element findOrCreatePropertiesElement(Document doc, Element testcase) {
    // Check if properties element already exists as first child
    NodeList children = testcase.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals("properties")) {
        return (Element) child;
      }
    }

    // Create new properties element and insert it as first child
    Element properties = doc.createElement("properties");
    if (testcase.hasChildNodes()) {
      testcase.insertBefore(properties, testcase.getFirstChild());
    } else {
      testcase.appendChild(properties);
    }
    return properties;
  }

  private TestMetadata findMetadataForTest(String className, String testName) {
    // Try to find metadata by matching class name and test name
    for (TestMetadata metadata : testMetadata.values()) {
      TestIdentifier test = metadata.testIdentifier;
      Optional<TestIdentifier> parent = testPlan.getParent(test);

      if (parent.isPresent()) {
        String metadataClassName = parent.get().getLegacyReportingName();
        String metadataTestName = test.getLegacyReportingName();

        // Remove parentheses from test name if present (e.g., "testName()" -> "testName")
        if (metadataTestName.endsWith("()")) {
          metadataTestName = metadataTestName.substring(0, metadataTestName.length() - 2);
        }

        if (className.equals(metadataClassName) && testName.equals(metadataTestName)) {
          return metadata;
        }
      }
    }
    return null;
  }

  private static class TestMetadata {
    TestIdentifier testIdentifier;
    Set<Property> properties;
  }
}
