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
package api;

import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.protocols.json.internal.unmarshall.JsonProtocolUnmarshaller;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageRequest;

final class BatchMeterUsageRequestParser {

  private static final JsonNodeParser JSON_NODE_PARSER = JsonNodeParser.create();
  private static final JsonProtocolUnmarshaller JSON_PROTOCOL_UNMARSHALLER =
      JsonProtocolUnmarshaller.builder()
          .protocolUnmarshallDependencies(
              JsonProtocolUnmarshaller.defaultProtocolUnmarshallDependencies())
          .build();

  private BatchMeterUsageRequestParser() {}

  static BatchMeterUsageRequest parse(String json) {
    return JSON_PROTOCOL_UNMARSHALLER.unmarshall(
        BatchMeterUsageRequest.builder(),
        SdkHttpFullResponse.builder().build(),
        JSON_NODE_PARSER.parse(json));
  }
}
