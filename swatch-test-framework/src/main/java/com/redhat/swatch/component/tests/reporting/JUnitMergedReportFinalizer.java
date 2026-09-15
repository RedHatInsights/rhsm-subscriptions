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

import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.TESTSUITE;
import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.TESTSUITES;
import static com.redhat.swatch.component.tests.utils.SurefireReportUtils.listSurefireReportFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Merges Surefire {@code TEST-*.xml} files and writes run metadata on the root element for S3
 * import. Primary entry point: {@link ComponentTestReporter} after the test plan finishes (pass or
 * fail).
 */
public final class JUnitMergedReportFinalizer {

  public static final String MERGED_REPORT_FILENAME = "merged-results.xml";

  private static final String TIME = "time";

  private JUnitMergedReportFinalizer() {}

  public static void writeMergedReport(Path reportsDir, String outputFilename) throws Exception {
    Path outputFile = reportsDir.resolve(outputFilename);
    List<Path> inputs = listSurefireReportFiles(reportsDir);
    if (inputs.isEmpty()) {
      return;
    }

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    DocumentBuilder builder = factory.newDocumentBuilder();

    Document mergedDoc = builder.newDocument();
    Element testSuites = mergedDoc.createElement(TESTSUITES);
    mergedDoc.appendChild(testSuites);
    for (Path input : inputs) {
      stripFlakesAttribute(input);
      Document doc = builder.parse(input.toFile());
      Element testsuite = doc.getDocumentElement();
      if (!TESTSUITE.equals(testsuite.getNodeName())) {
        throw new IllegalStateException(
            "unexpected root element in " + input + ": " + testsuite.getNodeName());
      }
      Element imported = (Element) mergedDoc.importNode(testsuite, true);
      testSuites.appendChild(imported);
      mergeTestSuiteAttributes(testSuites, imported);
    }

    Map<String, String> runMetadata = JUnitXmlMetadata.runMetadataFromSystemProperties();
    JUnitXmlMetadata.upsertRunMetadataOnElement(testSuites, runMetadata);

    var transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    transformer.setOutputProperty(OutputKeys.INDENT, "no");
    transformer.transform(new DOMSource(mergedDoc), new StreamResult(outputFile.toFile()));
    System.out.println("Wrote merged JUnit report: " + outputFile);
  }

  private static void stripFlakesAttribute(Path xmlFile) throws IOException {
    String content = Files.readString(xmlFile);
    String updated = content.replaceAll(" flakes=\"[0-9]*\"", "");
    if (!content.equals(updated)) {
      Files.writeString(xmlFile, updated);
    }
  }

  private static void mergeTestSuiteAttributes(Element target, Element source) {
    for (String attr : List.of("tests", "failures", "errors", "skipped")) {
      int total = parseInt(target.getAttribute(attr)) + parseInt(source.getAttribute(attr));
      target.setAttribute(attr, Integer.toString(total));
    }
    double time = parseDouble(target.getAttribute(TIME)) + parseDouble(source.getAttribute(TIME));
    target.setAttribute(TIME, Double.toString(time));
  }

  private static int parseInt(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    return Integer.parseInt(value);
  }

  private static double parseDouble(String value) {
    if (value == null || value.isBlank()) {
      return 0.0;
    }
    return Double.parseDouble(value);
  }
}
