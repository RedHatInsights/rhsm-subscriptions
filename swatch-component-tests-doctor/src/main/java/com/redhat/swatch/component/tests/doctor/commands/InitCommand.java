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

import com.redhat.swatch.component.tests.doctor.domain.IngestionMetadata;
import com.redhat.swatch.component.tests.doctor.services.IngestionService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command(
    name = "init",
    description = "Initialize the knowledge base by ingesting the rhsm-subscriptions codebase")
public class InitCommand implements Runnable {

  @CommandLine.Option(
      names = {"--rhsm-subscriptions"},
      description = "Path to the rhsm-subscriptions repository",
      required = true)
  private Path rhsmSubscriptionsPath;

  @CommandLine.Option(
      names = {"--force"},
      description = "Force re-initialization even if already initialized",
      defaultValue = "false")
  private boolean force;

  @CommandLine.Option(
      names = {"-v", "--verbose"},
      description = "Show detailed progress",
      defaultValue = "false")
  private boolean verbose;

  @Inject EmbeddingStore store;

  @Inject EmbeddingModel embeddingModel;

  @Inject IngestionService ingestionService;

  @Override
  public void run() {
    Log.info("Initializing Component Tests Doctor");
    Log.info("================================================================================");

    // Validate the path exists
    if (!Files.exists(rhsmSubscriptionsPath)) {
      Log.errorf("Path does not exist: %s", rhsmSubscriptionsPath);
      Log.error("Please provide a valid path to the rhsm-subscriptions repository.");
      return;
    }

    if (!Files.isDirectory(rhsmSubscriptionsPath)) {
      Log.errorf("Path is not a directory: %s", rhsmSubscriptionsPath);
      return;
    }

    String sourcePath = rhsmSubscriptionsPath.toAbsolutePath().toString();

    // Check if already initialized
    if (!force && ingestionService.isIngested(sourcePath)) {
      IngestionMetadata lastIngestion = ingestionService.getLastIngestion(sourcePath);
      Log.warn("Knowledge base already initialized!");

      Log.infof("Source: %s", lastIngestion.sourcePath);
      Log.infof("Initialized at: %s", lastIngestion.ingestedAt);
      Log.infof("Documents: %d", lastIngestion.documentCount);
      Log.infof("Skipped: %d", lastIngestion.skippedCount);
      Log.infof("Duration: %d ms", lastIngestion.durationMs);

      Log.info("Use --force to re-initialize");
      return;
    }

    if (force && ingestionService.isIngested(sourcePath)) {
      Log.info("Force re-initialization requested");
      Log.info("Clearing previous knowledge base...");
    }

    // Show initialization info
    Log.infof("Repository: %s", rhsmSubscriptionsPath.toAbsolutePath());
    Log.info("Processing...");

    try {
      // Perform the ingestion
      ingestionService.ingestDocuments(store, embeddingModel, rhsmSubscriptionsPath, true);

      Log.info("Initialization completed successfully!");

      Log.info("Next steps:");
      Log.info("   - Use 'status' to view ingestion details");
      Log.info("   - Use 'investigate <test-name>' to analyze test failures");

    } catch (Exception e) {

      Log.errorf("Error during initialization: %s", e.getMessage());
      if (verbose) {

        e.printStackTrace();
      }

      Log.info("Try running with --verbose for more details");
    }
  }
}
