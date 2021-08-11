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
package org.candlepin.subscriptions.clowder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.JsonFileSource;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * Class to load the Clowder JSON configuration exposed by Clowder as Kubernetes secret (located by
 * default at cdapp/cdappconfig.json)
 */
@Component
public class ClowderJsonFileSource extends JsonFileSource<ClowderJson>
    implements FactoryBean<ClowderJson> {

  public static final String EMPTY_JSON = "{}";

  public ClowderJsonFileSource(
      ApplicationProperties properties, ApplicationClock clock, ObjectMapper mapper) {
    super(
        properties.getClowderJsonResourceLocation(),
        clock.getClock(),
        properties.getClowderJsonCacheTtl(),
        properties.isStrictResourceLoadingMode(),
        mapper);
  }

  @Override
  protected ClowderJson getDefault() {
    try {
      return new ClowderJson(
          new ByteArrayInputStream(EMPTY_JSON.getBytes(StandardCharsets.UTF_8)), mapper);
    } catch (IOException e) {
      // Given the default JSON is static and known to be valid, this exception should never happen
      throw new IllegalArgumentException("Unable to create default ClowderJson");
    }
  }

  @Override
  protected ClowderJson parse(InputStream s) throws IOException {
    return new ClowderJson(s, mapper);
  }

  @Override
  public ClowderJson getObject() throws Exception {
    return getValue();
  }

  @Override
  public Class<?> getObjectType() {
    return ClowderJson.class;
  }

  @Override
  public boolean isSingleton() {
    return false;
  }
}
