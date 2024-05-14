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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.exception.ExportServiceException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class JsonExportFileWriter implements ExportFileWriter {

  private final ObjectMapper objectMapper;

  @Override
  public void write(
      File output, DataMapperService<?> dataMapper, Stream<?> data, ExportServiceRequest request) {
    log.debug("Writing Json for request {}", request.getExportRequestUUID());

    try (data;
        FileOutputStream stream = new FileOutputStream(output);
        JsonGenerator jGenerator = objectMapper.createGenerator(stream, JsonEncoding.UTF8)) {
      jGenerator.writeStartObject();
      jGenerator.writeArrayFieldStart("data");
      var serializerProvider = objectMapper.getSerializerProviderInstance();
      var serializer =
          serializerProvider.findTypedValueSerializer(dataMapper.getExportItemClass(), false, null);
      data.flatMap(i -> dataMapper.mapDataItem(i, request).stream())
          .forEach(
              item -> {
                try {
                  serializer.serialize(item, jGenerator, serializerProvider);
                } catch (IOException e) {
                  handleIOException(item, request, e);
                }
              });
      jGenerator.writeEndArray();
      jGenerator.writeEndObject();
    } catch (IOException e) {
      log.error("Error writing the Json payload for request {}", request.getExportRequestUUID(), e);
      throw new ExportServiceException(
          Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error writing the Json payload");
    }
  }

  private void handleIOException(Object item, ExportServiceRequest request, IOException e) {
    log.error(
        "Error serializing the data item {} for request {}",
        item,
        request.getExportRequestUUID(),
        e);
    throw new ExportServiceException(
        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error writing the payload");
  }
}
