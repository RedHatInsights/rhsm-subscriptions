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
package org.candlepin.subscriptions.prometheus.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.junit.jupiter.api.Test;

class ApiProviderFactoryTest {

  @Test
  void canUseStubClient() throws Exception {
    HttpClientProperties props = new HttpClientProperties();
    props.setUseStub(true);

    ApiProviderFactory factory = new ApiProviderFactory(props);
    ApiProvider api = factory.getObject();
    assertTrue(api instanceof StubApiProvider);
    assertStubFile(api.queryApi().query(null, null, null));
    assertStubFile(api.queryRangeApi().queryRange(null, null, null, null, null));
  }

  @Test
  void checkPropertyInitialization() throws Exception {
    HttpClientProperties props = new HttpClientProperties();
    assertFalse(props.isUseStub());

    ApiProviderFactory factory = new ApiProviderFactory(props);
    ApiProvider api = factory.getObject();
    assertTrue(api instanceof ApiProviderImpl);
  }

  private void assertStubFile(File file) {
    assertNotNull(file);
    assertTrue(file.exists());
  }
}
