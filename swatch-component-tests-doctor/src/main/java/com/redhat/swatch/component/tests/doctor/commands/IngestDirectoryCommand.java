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

import com.redhat.swatch.component.tests.doctor.services.IngestionService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command(
    name = "ingest",
    description = "Manually ingest additional documents into the knowledge base")
public class IngestDirectoryCommand implements Runnable {

  @CommandLine.Parameters(index = "0", description = "Directory path to ingest")
  private Path directoryPath;

  @CommandLine.Option(
      names = {"--clear"},
      description = "Clear existing documents before ingesting (default: false)",
      defaultValue = "false")
  private boolean clearExisting;

  @CommandLine.Option(
      names = {"-v", "--verbose"},
      description = "Show detailed progress",
      defaultValue = "false")
  private boolean verbose;

  @Inject EmbeddingStore store;
  @Inject EmbeddingModel embeddingModel;
  @Inject IngestionService ingestionConfig;

  @Override
  public void run() {
    if (!Files.exists(directoryPath)) {
      Log.errorf("Directory does not exist: %s", directoryPath);
      return;
    }

    if (!Files.isDirectory(directoryPath)) {
      Log.errorf("Path is not a directory: %s", directoryPath);
      return;
    }

    Log.info("Starting document ingestion...");
    Log.infof("Source: %s", directoryPath.toAbsolutePath());
    Log.infof("Clear existing: %s", clearExisting);

    try {
      long startTime = System.currentTimeMillis();

      ingestionConfig.ingestDocuments(store, embeddingModel, directoryPath, clearExisting);

      long duration = System.currentTimeMillis() - startTime;

      Log.info("Ingestion completed successfully!");
      Log.infof("Duration: %d ms", duration);

    } catch (Exception e) {
      Log.errorf("Error during ingestion: %s", e.getMessage());
      if (verbose) {
        e.printStackTrace();
      }
    }
  }
}
