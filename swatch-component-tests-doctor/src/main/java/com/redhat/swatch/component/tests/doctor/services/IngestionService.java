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
package com.redhat.swatch.component.tests.doctor.services;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

import com.redhat.swatch.component.tests.doctor.domain.IngestionMetadata;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class IngestionService {

  @ConfigProperty(name = "ingest.allowed-extensions")
  String allowedExtensionsStr;

  @ConfigProperty(name = "ingest.max-file-size", defaultValue = "5242880") // 5MB
  long maxFileSize;

  @ConfigProperty(name = "ingest.min-file-size", defaultValue = "10")
  long minFileSize;

  @ConfigProperty(name = "ingest.skip-directories")
  String skipDirectoriesStr;

  @ConfigProperty(name = "ingest.chunk-size", defaultValue = "500")
  int chunkSize;

  @ConfigProperty(name = "ingest.chunk-overlap", defaultValue = "50")
  int chunkOverlap;

  private Set<String> allowedExtensions;
  private Set<String> skipDirectories;

  @PostConstruct
  void init() {
    allowedExtensions = Set.of(allowedExtensionsStr.split(","));
    skipDirectories = Set.of(skipDirectoriesStr.split(","));
  }

  /**
   * List all ingestion metadata records.
   *
   * @return list of all ingestion metadata
   */
  @Transactional
  public List<IngestionMetadata> listAllIngestions() {
    return IngestionMetadata.listAll();
  }

  /**
   * Check if a path has been ingested.
   *
   * @param path the source path to check
   * @return true if the path has been ingested
   */
  @Transactional
  public boolean isIngested(String path) {
    return IngestionMetadata.isIngested(path);
  }

  /**
   * Get the last ingestion metadata for a path.
   *
   * @param path the source path to check
   * @return the last ingestion metadata or null if not found
   */
  @Transactional
  public IngestionMetadata getLastIngestion(String path) {
    return IngestionMetadata.getLastIngestion(path);
  }

  /**
   * Ingests documents from the specified path into the vector database. Loads documents from the
   * specified path, filters them, and stores them in the vector database. Also saves metadata about
   * the ingestion for tracking purposes.
   *
   * @param store the embedding store where documents will be stored
   * @param embeddingModel the model used to generate embeddings
   * @param documentsPath the root path to load documents from
   * @param clearExisting whether to clear existing documents before ingesting
   */
  @Transactional
  public void ingestDocuments(
      EmbeddingStore store,
      EmbeddingModel embeddingModel,
      Path documentsPath,
      boolean clearExisting) {

    long startTime = System.currentTimeMillis();
    String sourcePath = documentsPath.toAbsolutePath().toString();

    if (clearExisting) {
      Log.info("Clearing existing documents from store...");
      store.removeAll();
      // Also clear metadata
      IngestionMetadata.clearIngestionMetadata(sourcePath);
    }

    Log.info("Loading documents recursively from: " + documentsPath);

    // Load documents with filtering
    IngestionStats stats = loadFilteredDocuments(documentsPath);
    Log.info("Loaded " + stats.documents.size() + " documents, skipped " + stats.skippedCount);

    if (stats.documents.isEmpty()) {
      Log.warn("No documents found to ingest!");
      return;
    }

    EmbeddingStoreIngestor ingestor =
        EmbeddingStoreIngestor.builder()
            .embeddingStore(store)
            .embeddingModel(embeddingModel)
            .documentSplitter(recursive(chunkSize, chunkOverlap))
            .build();

    Log.info("Ingesting documents into vector store...");
    ingestor.ingest(stats.documents);

    long duration = System.currentTimeMillis() - startTime;

    // Save ingestion metadata
    IngestionMetadata metadata = new IngestionMetadata();
    metadata.sourcePath = sourcePath;
    metadata.ingestedAt = Instant.now();
    metadata.documentCount = stats.documents.size();
    metadata.skippedCount = stats.skippedCount;
    metadata.durationMs = duration;
    metadata.persist();

    Log.info("Documents ingested successfully in " + duration + "ms");
    Log.info("Metadata saved for future reference");
  }

  /**
   * Loads documents with filtering by extension and size. This prevents loading binary files, large
   * files, empty files, and files from build directories.
   *
   * @param rootPath the root directory to start loading from
   * @return ingestion statistics including documents and skip count
   */
  private IngestionStats loadFilteredDocuments(Path rootPath) {
    List<Document> documents = new ArrayList<>();
    int[] counters = {0, 0}; // [processed, skipped]

    try {
      Files.walkFileTree(
          rootPath,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              // Skip common directories we don't want to process
              String pathStr = file.toString();
              if (shouldSkipPath(pathStr)) {
                return FileVisitResult.CONTINUE;
              }

              // Check file extension
              String fileName = file.getFileName().toString();
              if (!hasAllowedExtension(fileName)) {
                counters[1]++; // skipped
                return FileVisitResult.CONTINUE;
              }

              // Check file size
              try {
                long size = Files.size(file);

                // Skip files that are too small (likely empty or blank)
                if (size < minFileSize) {
                  Log.debug("Skipping small file: " + file + " (" + size + " bytes)");
                  counters[1]++; // skipped
                  return FileVisitResult.CONTINUE;
                }

                // Skip files that are too large
                if (size > maxFileSize) {
                  Log.debug("Skipping large file: " + file + " (" + size + " bytes)");
                  counters[1]++; // skipped
                  return FileVisitResult.CONTINUE;
                }

                // Try to load the document
                try {
                  Document doc = FileSystemDocumentLoader.loadDocument(file);
                  documents.add(doc);
                  counters[0]++; // processed

                  if (counters[0] % 100 == 0) {
                    Log.info("Processed " + counters[0] + " files...");
                  }
                } catch (dev.langchain4j.data.document.BlankDocumentException e) {
                  // Document is blank (only whitespace)
                  Log.debug("Skipping blank document: " + file);
                  counters[1]++; // skipped
                }

              } catch (IOException e) {
                Log.warn("Could not process file: " + file + " - " + e.getMessage());
                counters[1]++; // skipped
              } catch (Exception e) {
                // Catch any other exceptions
                Log.warn("Error loading document: " + file + " - " + e.getMessage());
                counters[1]++; // skipped
              }

              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              Log.warn("Could not access file: " + file + " - " + exc.getMessage());
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      Log.error("Error walking file tree: " + e.getMessage(), e);
    }

    Log.info("Files processed: " + counters[0] + ", skipped: " + counters[1]);
    return new IngestionStats(documents, counters[1]);
  }

  /**
   * Checks if the file has an allowed extension.
   *
   * @param fileName the name of the file to check
   * @return true if the file extension is in the allowed list
   */
  private boolean hasAllowedExtension(String fileName) {
    String lowerName = fileName.toLowerCase();
    return allowedExtensions.stream().anyMatch(lowerName::endsWith);
  }

  /**
   * Checks if we should skip this path (build directories, version control, etc).
   *
   * @param path the full path to check
   * @return true if the path should be skipped
   */
  private boolean shouldSkipPath(String path) {
    String lowerPath = path.toLowerCase();
    return skipDirectories.stream()
        .anyMatch(dir -> lowerPath.contains("/" + dir + "/") || lowerPath.endsWith("/" + dir));
  }

  /** Helper class to hold ingestion statistics */
  private static class IngestionStats {
    final List<Document> documents;
    final int skippedCount;

    IngestionStats(List<Document> documents, int skippedCount) {
      this.documents = documents;
      this.skippedCount = skippedCount;
    }
  }
}
