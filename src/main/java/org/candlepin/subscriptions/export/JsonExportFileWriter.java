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
package org.candlepin.subscriptions.export;

import static com.redhat.swatch.export.ExportRequestHandler.INTERNAL_ERROR;

import com.redhat.swatch.export.DataMapperService;
import com.redhat.swatch.export.ExportFileWriter;
import com.redhat.swatch.export.ExportServiceException;
import com.redhat.swatch.export.ExportServiceRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@AllArgsConstructor
public class JsonExportFileWriter implements ExportFileWriter {

  private final JsonMapper objectMapper;

  @Override
  public void write(
      File output, DataMapperService<?> dataMapper, Stream<?> data, ExportServiceRequest request) {
    log.debug("Writing Json for request {}", request.getExportRequestUUID());

    try (data;
        FileOutputStream stream = new FileOutputStream(output);
        JsonGenerator jGenerator = objectMapper.createGenerator(stream, JsonEncoding.UTF8)) {
      jGenerator.writeStartObject();
      jGenerator.writeName("data");
      jGenerator.writeStartArray();
      var writer = objectMapper.writerFor(dataMapper.getExportItemClass());
      data.flatMap(i -> dataMapper.mapDataItem(i, request).stream())
          .forEach(item -> writer.writeValue(jGenerator, item));
      jGenerator.writeEndArray();
      jGenerator.writeEndObject();
    } catch (IOException | JacksonException e) {
      log.error("Error writing the Json payload for request {}", request.getExportRequestUUID(), e);
      throw new ExportServiceException(INTERNAL_ERROR, "Error writing the Json payload");
    }
  }
}
