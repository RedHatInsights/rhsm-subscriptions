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
package com.redhat.swatch.component.tests.doctor.commands;

import com.redhat.swatch.component.tests.doctor.DoctorAgent;
import com.redhat.swatch.component.tests.doctor.domain.IngestionMetadata;
import com.redhat.swatch.component.tests.doctor.services.IngestionService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import picocli.CommandLine;

@CommandLine.Command(
    name = "investigate",
    description = "Investigate a test failure or all failures in a module using the AI agent")
public class InvestigateCommand implements Runnable {

  // Known modules for auto-detection
  private static final List<String> KNOWN_MODULES =
      List.of("swatch-contracts", "swatch-utilization");

  @CommandLine.Parameters(
      index = "0",
      description =
          "Name of the test to investigate (e.g., UtilizationSummaryConsumerComponentTest) "
              + "or module path (e.g., swatch-contracts)")
  private String testNameOrModule;

  @CommandLine.Option(
      names = {"--in-folder", "-f"},
      description = "Folder where the test is located (e.g., swatch-contracts/ct)",
      required = false)
  private String testFolder;

  @CommandLine.Option(
      names = {"--surefire-report"},
      description = "Path to surefire reports (default: auto-detected)",
      required = false)
  private String surefireReportPath;

  @CommandLine.Option(
      names = {"-v", "--verbose"},
      description = "Show detailed investigation process",
      defaultValue = "false")
  private boolean verbose;

  @Inject DoctorAgent agent;

  @Inject IngestionService ingestionService;

  @Override
  public void run() {
    String repoBasePath = getRepositoryBasePath();
    if (repoBasePath == null) {
      Log.error(
          "No ingestion found. Please run 'init' command first to initialize the knowledge base.");
      return;
    }

    // Detect if input is a module or a test name
    boolean isModule = KNOWN_MODULES.contains(testNameOrModule);

    if (isModule) {
      investigateModule(testNameOrModule, repoBasePath);
    } else {
      investigateSingleTest(testNameOrModule, repoBasePath);
    }
  }

  private void investigateSingleTest(String testName, String repoBasePath) {
    Log.infof("Investigating test: %s", testName);

    if (verbose) {
      Log.infof("Repository base path: %s", repoBasePath);
    }

    // Try to find the test location automatically
    String detectedTestFolder = testFolder;
    if (detectedTestFolder == null) {
      detectedTestFolder = findTestLocation(repoBasePath, testName);
      if (detectedTestFolder != null && verbose) {
        Log.infof("Auto-detected test location: %s", detectedTestFolder);
      }
    }

    // Find surefire reports
    Path surefireReportsPath = findSurefireReports(repoBasePath, detectedTestFolder);
    if (surefireReportsPath == null) {
      Log.warn("Could not find surefire reports. Continuing without error details.");
    }

    if (verbose && surefireReportsPath != null) {
      Log.infof("Using surefire reports: %s", surefireReportsPath);
    }

    // Extract error from surefire report
    String errorMessage = null;
    if (surefireReportsPath != null) {
      errorMessage = extractErrorFromSurefireReport(surefireReportsPath.toString(), testName);
    }

    // Investigate the test
    investigateTest(testName, detectedTestFolder, errorMessage, repoBasePath);
  }

  private void investigateModule(String modulePath, String repoBasePath) {
    Log.infof("Investigating all failed tests in module: %s", modulePath);

    if (verbose) {
      Log.infof("Repository base path: %s", repoBasePath);
    }

    Path moduleFullPath = Paths.get(repoBasePath, modulePath);
    if (!Files.exists(moduleFullPath)) {
      Log.errorf("Module not found at %s", moduleFullPath);
      return;
    }

    // Find surefire reports
    Path surefireReportsPath = findSurefireReports(repoBasePath, modulePath);
    if (surefireReportsPath == null) {
      Log.errorf("No surefire reports found for module %s", modulePath);
      Log.error("Make sure you have run the tests first.");
      return;
    }

    if (verbose) {
      Log.infof("Reading surefire reports from: %s", surefireReportsPath);
    }

    // Find all failed tests
    List<FailedTest> failedTests = findFailedTestsInModule(surefireReportsPath);

    if (failedTests.isEmpty()) {
      Log.infof("No failed tests found in %s", modulePath);
      return;
    }

    Log.infof("Found %d failed test(s)", failedTests.size());

    // Investigate each failed test
    for (int i = 0; i < failedTests.size(); i++) {
      FailedTest failedTest = failedTests.get(i);
      Log.info("================================================================================");
      Log.infof("Test %d/%d: %s", i + 1, failedTests.size(), failedTest.testName);
      Log.info("================================================================================");

      investigateTest(failedTest.testName, modulePath, failedTest.errorMessage, repoBasePath);
    }

    Log.info("================================================================================");
    Log.infof("Investigation complete for %s", modulePath);
    Log.info("================================================================================");
  }

  /**
   * Core investigation logic - reused by both single test and module investigation.
   *
   * @param testName name of the test
   * @param modulePath module path (can be null for single test)
   * @param errorMessage error message from surefire report (can be null)
   * @param repoBasePath repository base path
   */
  private void investigateTest(
      String testName, String modulePath, String errorMessage, String repoBasePath) {
    // Build context for the agent
    StringBuilder context = new StringBuilder();
    context.append("# Test Failure Investigation Request\n\n");

    context.append("## Test Details\n");
    context.append("- **Test Name**: ").append(testName).append("\n");
    if (modulePath != null) {
      context.append("- **Module**: ").append(modulePath).append("\n");
    }
    context.append("- **Repository absolute path**: ").append(repoBasePath).append("\n\n");

    if (errorMessage != null) {
      context.append("## Test Failure\n\n");
      context.append("```\n");
      context.append(errorMessage);
      context.append("```\n\n");
    } else {
      context.append("## Test Failure\n\n");
      context.append("No surefire report found. Test may not have been executed yet.\n\n");
    }

    context.append("## Your Task\n\n");
    context.append("Follow the process defined in your system prompt:\n");
    context.append("1. Analyze the test failure above\n");
    context.append("2. Identify the class/method being tested (look at the stack trace)\n");
    context.append(
        "3. Use searchInFile() to find problematic code (commented annotations, missing code, etc.)\n");
    context.append("4. Only check git history if the code looks correct\n\n");

    if (verbose) {
      Log.info("Context being sent to agent:");
      Log.info("--------------------------------------------------------------------------------");
      Log.info(context.toString());
      Log.info("--------------------------------------------------------------------------------");
    }

    Log.info("Agent analysis:");
    Log.info("--------------------------------------------------------------------------------");
    String response = agent.chat(context.toString());
    Log.info(response);
    Log.info("--------------------------------------------------------------------------------");
  }

  /**
   * Find surefire reports directory for a module or test folder. Tries ct/target first, then
   * target.
   *
   * @param repoBasePath repository base path
   * @param moduleOrFolder module or folder path (can be null)
   * @return Path to surefire reports, or null if not found
   */
  private Path findSurefireReports(String repoBasePath, String moduleOrFolder) {
    if (moduleOrFolder == null) {
      return null;
    }

    // Try ct/target/surefire-reports first
    Path ctPath = Paths.get(repoBasePath, moduleOrFolder, "ct", "target", "surefire-reports");
    if (Files.exists(ctPath)) {
      return ctPath;
    }

    // Fallback to target/surefire-reports
    Path targetPath = Paths.get(repoBasePath, moduleOrFolder, "target", "surefire-reports");
    if (Files.exists(targetPath)) {
      return targetPath;
    }

    // Custom path if provided
    if (surefireReportPath != null) {
      Path customPath = Paths.get(surefireReportPath);
      if (Files.exists(customPath)) {
        return customPath;
      }
    }

    return null;
  }

  private List<FailedTest> findFailedTestsInModule(Path surefireReportsPath) {
    List<FailedTest> failedTests = new ArrayList<>();

    try (Stream<Path> files = Files.list(surefireReportsPath)) {
      files
          .filter(file -> file.getFileName().toString().startsWith("TEST-"))
          .filter(file -> file.getFileName().toString().endsWith(".xml"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);
                  if (content.contains("<failure") || content.contains("<error")) {
                    String testName = extractTestNameFromFile(file.getFileName().toString());
                    String errorMessage = extractErrorMessageFromXml(content);
                    if (errorMessage != null) {
                      failedTests.add(new FailedTest(testName, errorMessage));
                    }
                  }
                } catch (IOException e) {
                  if (verbose) {
                    Log.warnf("Error reading file: %s", file);
                  }
                }
              });
    } catch (IOException e) {
      Log.errorf("Error reading surefire reports: %s", e.getMessage());
    }

    return failedTests;
  }

  private String getRepositoryBasePath() {
    List<IngestionMetadata> ingestions = ingestionService.listAllIngestions();
    if (ingestions.isEmpty()) {
      return null;
    }
    return ingestions.get(0).sourcePath;
  }

  private String extractErrorFromSurefireReport(String surefireReportPath, String testName) {
    if (surefireReportPath == null) {
      return null;
    }

    Path surefireDir = Path.of(surefireReportPath);
    if (!Files.exists(surefireDir)) {
      return null;
    }

    try {
      String searchName = testName;
      if (testName.endsWith("ComponentTest")) {
        searchName = testName.replace("ComponentTest", "Test");
      }

      final String finalSearchName = searchName;
      List<Path> matchingReports =
          Files.list(surefireDir)
              .filter(
                  file -> {
                    String fileName = file.getFileName().toString();
                    return (fileName.contains(testName) || fileName.contains(finalSearchName))
                        && fileName.endsWith(".xml");
                  })
              .toList();

      if (matchingReports.isEmpty()) {
        if (verbose) {
          Log.warnf("No surefire reports found matching: %s", testName);
        }
        return null;
      }

      if (verbose) {
        Log.infof("Found surefire report: %s", matchingReports.get(0).getFileName());
      }

      String content = Files.readString(matchingReports.get(0));
      return extractErrorMessageFromXml(content);

    } catch (IOException e) {
      if (verbose) {
        Log.warnf("Error reading surefire report: %s", e.getMessage());
      }
      return null;
    }
  }

  private String extractTestNameFromFile(String fileName) {
    String name = fileName.replace(".xml", "");
    if (name.startsWith("TEST-")) {
      name = name.substring(5);
    }
    int lastDot = name.lastIndexOf('.');
    if (lastDot != -1) {
      name = name.substring(lastDot + 1);
    }
    return name;
  }

  private String extractErrorMessageFromXml(String xmlContent) {
    StringBuilder error = new StringBuilder();

    int failureStart = xmlContent.indexOf("<failure");
    int errorStart = xmlContent.indexOf("<error");

    int tagStart = -1;
    String tagName = null;
    if (failureStart != -1 && (errorStart == -1 || failureStart < errorStart)) {
      tagStart = failureStart;
      tagName = "failure";
    } else if (errorStart != -1) {
      tagStart = errorStart;
      tagName = "error";
    }

    if (tagStart != -1) {
      int messageStart = xmlContent.indexOf("message=\"", tagStart);
      if (messageStart != -1) {
        messageStart += 9;
        int messageEnd = xmlContent.indexOf("\"", messageStart);
        if (messageEnd != -1) {
          String message = xmlContent.substring(messageStart, messageEnd);
          message =
              message
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&amp;", "&");
          error.append(message).append("\n\n");
        }
      }

      int contentStart = xmlContent.indexOf(">", tagStart) + 1;
      int contentEnd = xmlContent.indexOf("</" + tagName + ">", contentStart);
      if (contentStart != -1 && contentEnd != -1) {
        String stackTrace = xmlContent.substring(contentStart, contentEnd).trim();
        if (!stackTrace.isEmpty()) {
          String[] lines = stackTrace.split("\n");
          int maxLines = Math.min(20, lines.length);
          for (int i = 0; i < maxLines; i++) {
            error.append(lines[i]).append("\n");
          }
          if (lines.length > 20) {
            error.append("... (truncated)\n");
          }
        }
      }
    }

    return error.length() > 0 ? error.toString() : null;
  }

  private String findTestLocation(String repoBasePath, String testName) {
    Path repoPath = Paths.get(repoBasePath);
    if (!Files.exists(repoPath)) {
      return null;
    }

    try {
      try (Stream<Path> paths = Files.walk(repoPath)) {
        return paths
            .filter(Files::isRegularFile)
            .filter(
                path -> {
                  String fileName = path.getFileName().toString();
                  if (fileName.equals(testName + ".java") || fileName.equals(testName + ".kt")) {
                    return true;
                  }
                  if (testName.endsWith("ComponentTest")) {
                    String withoutComponent = testName.replace("ComponentTest", "Test");
                    if (fileName.equals(withoutComponent + ".java")
                        || fileName.equals(withoutComponent + ".kt")) {
                      return true;
                    }
                  }
                  return fileName.contains(testName)
                      && (fileName.endsWith(".java") || fileName.endsWith(".kt"));
                })
            .findFirst()
            .map(
                testPath -> {
                  if (verbose) {
                    Log.infof("Found test file: %s", testPath);
                  }
                  Path relativePath = repoPath.relativize(testPath.getParent());
                  String relativeStr = relativePath.toString();

                  if (relativeStr.contains("/src/test/java")) {
                    return relativeStr.substring(0, relativeStr.indexOf("/src/test/java"));
                  } else if (relativeStr.contains("/ct/src/test/java")) {
                    return relativeStr.substring(0, relativeStr.indexOf("/ct/src/test/java"))
                        + "/ct";
                  } else if (relativeStr.contains("/ct/java")) {
                    return relativeStr.substring(0, relativeStr.indexOf("/ct/java")) + "/ct";
                  } else if (relativeStr.contains("/java/")) {
                    return relativeStr.substring(0, relativeStr.indexOf("/java"));
                  }
                  return null;
                })
            .orElse(null);
      }
    } catch (IOException e) {
      if (verbose) {
        Log.warnf("Error searching for test: %s", e.getMessage());
      }
      return null;
    }
  }

  /** Simple data class to hold failed test information */
  private static class FailedTest {
    final String testName;
    final String errorMessage;

    FailedTest(String testName, String errorMessage) {
      this.testName = testName;
      this.errorMessage = errorMessage;
    }
  }
}
