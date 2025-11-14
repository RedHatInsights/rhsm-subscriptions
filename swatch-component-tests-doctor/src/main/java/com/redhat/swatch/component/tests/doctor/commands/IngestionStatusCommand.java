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
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(name = "status", description = "Show ingestion status and metadata")
public class IngestionStatusCommand implements Runnable {

  @Inject IngestionService ingestionService;

  @Override
  public void run() {
    Log.info("Ingestion Status");
    Log.info("================================================================================");

    List<IngestionMetadata> ingestions = ingestionService.listAllIngestions();

    if (ingestions.isEmpty()) {
      Log.info("No ingestions found. Use 'init' command to initialize the knowledge base.");
      return;
    }

    for (IngestionMetadata ingestion : ingestions) {
      Log.info("Source: " + ingestion.sourcePath);
      Log.info("   Ingested at: " + ingestion.ingestedAt);
      Log.info("   Documents: " + ingestion.documentCount);
      Log.info("   Skipped: " + ingestion.skippedCount);
      Log.info("   Duration: " + ingestion.durationMs + "ms");
    }
  }
}
