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
package com.redhat.swatch.component.tests.doctor.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entity to track ingestion metadata. Stores information about when and what was ingested into the
 * vector store.
 */
@Entity
@Table(
    name = "ingestion_metadata",
    indexes = {
      @jakarta.persistence.Index(name = "idx_source_path", columnList = "sourcePath", unique = true)
    })
public class IngestionMetadata extends PanacheEntity {

  /** The source path that was ingested */
  @Column(nullable = false, length = 1024)
  public String sourcePath;

  /** When the ingestion occurred */
  @Column(nullable = false)
  public Instant ingestedAt;

  /** Number of documents that were ingested */
  @Column(nullable = false)
  public int documentCount;

  /** Number of files that were skipped */
  @Column(nullable = false)
  public int skippedCount;

  /** Duration of the ingestion in milliseconds */
  @Column(nullable = false)
  public long durationMs;

  /** Hash of the directory structure/content (optional, for detecting changes) */
  @Column public String contentHash;

  /** Check if a path has been ingested */
  public static boolean isIngested(String path) {
    return find("sourcePath", path).count() > 0;
  }

  /** Get the last ingestion metadata for a path */
  public static IngestionMetadata getLastIngestion(String path) {
    return find("sourcePath = ?1 order by ingestedAt desc", path).firstResult();
  }

  /** Delete ingestion metadata for a path */
  public static void clearIngestionMetadata(String path) {
    delete("sourcePath", path);
  }
}
