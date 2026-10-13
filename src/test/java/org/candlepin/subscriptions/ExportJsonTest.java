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
package org.candlepin.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.redhat.swatch.export.DataMapperService;
import com.redhat.swatch.export.ExportServiceException;
import com.redhat.swatch.export.ExportServiceRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.candlepin.subscriptions.export.CsvExportFileWriter;
import org.candlepin.subscriptions.export.JsonExportFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportJsonTest {
  @TempDir Path directory;
  private final ApplicationConfiguration configuration = new ApplicationConfiguration();
  private final ExportServiceRequest request = mock(ExportServiceRequest.class);
  private final OffsetDateTime timestamp = OffsetDateTime.parse("2026-01-02T03:04:05Z");
  private final DataMapperService<String> dataMapper =
      new DataMapperService<>() {
        @Override
        public List<Object> mapDataItem(String name, ExportServiceRequest exportRequest) {
          return List.of(new ExportRow(name, timestamp));
        }

        @Override
        public Class<String> getDataClass() {
          return String.class;
        }

        @Override
        public Class<?> getExportItemClass() {
          return ExportRow.class;
        }
      };

  @Test
  void writesAllJsonRowsInsideDataEnvelopeAndClosesInput() throws IOException {
    var mapper = configuration.objectMapper(new ApplicationProperties());
    var output = directory.resolve("export.json");
    var closed = new AtomicBoolean();
    new JsonExportFileWriter(mapper)
        .write(
            output.toFile(),
            dataMapper,
            Stream.of("host,one", "host\"two").onClose(() -> closed.set(true)),
            request);
    var json = mapper.readTree(Files.readString(output));
    assertEquals(2, json.get("data").size());
    assertEquals("host,one", json.get("data").get(0).get("instance_id").asText());
    assertEquals("host\"two", json.get("data").get(1).get("instance_id").asText());
    assertEquals(timestamp.toString(), json.get("data").get(0).get("last_seen").asText());
    assertTrue(closed.get());
  }

  @Test
  void writesEmptyJsonDataArray() throws IOException {
    var mapper = configuration.objectMapper(new ApplicationProperties());
    var output = directory.resolve("empty.json");
    new JsonExportFileWriter(mapper).write(output.toFile(), dataMapper, Stream.empty(), request);
    assertEquals(mapper.readTree("{\"data\":[]}"), mapper.readTree(Files.readString(output)));
  }

  @Test
  void writesCsvHeadersEscapingAndIsoDates() throws IOException {
    var output = directory.resolve("export.csv");
    new CsvExportFileWriter(configuration.csvMapper())
        .write(output.toFile(), dataMapper, Stream.of("host,one", "host\"two"), request);
    assertEquals(
        """
        instance_id,last_seen
        "host,one",2026-01-02T03:04:05Z
        "host""two",2026-01-02T03:04:05Z
        """,
        Files.readString(output));
  }

  @Test
  void closesInputAndReportsIoFailure() {
    var closed = new AtomicBoolean();
    var mapper = configuration.objectMapper(new ApplicationProperties());
    var writer = new JsonExportFileWriter(mapper);
    var error =
        assertThrows(
            ExportServiceException.class,
            () ->
                writer.write(
                    directory.toFile(),
                    dataMapper,
                    Stream.of("host").onClose(() -> closed.set(true)),
                    request));
    assertEquals(500, error.getStatus());
    assertTrue(closed.get());
  }

  @JsonPropertyOrder({"instance_id", "last_seen"})
  record ExportRow(
      @JsonProperty("instance_id") String instanceId,
      @JsonProperty("last_seen") OffsetDateTime lastSeen) {}
}
