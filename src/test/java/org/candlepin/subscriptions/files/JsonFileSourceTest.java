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
package org.candlepin.subscriptions.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JsonFileSourceTest {

  public static final String TEST_ITEM =
      "{\n" + "    \"id\": 1,\n" + "    \"itemName\": \"theItem\"\n" + "}";

  public static class Item {
    public int id;
    public String itemName;

    public int getId() {
      return id;
    }

    public void setId(int id) {
      this.id = id;
    }

    public String getItemName() {
      return itemName;
    }

    public void setItemName(String itemName) {
      this.itemName = itemName;
    }
  }

  public static class DummyJsonFileSource extends JsonFileSource<Item> {
    protected DummyJsonFileSource(
        String resourceLocation, Clock clock, Duration cacheTtl, ObjectMapper mapper) {
      super(resourceLocation, clock, cacheTtl, true, mapper);
    }

    @Override
    protected Item getDefault() {
      return null;
    }

    @Override
    public Class<?> getObjectType() {
      return Item.class;
    }
  }

  @Test
  void parseTest() throws IOException {
    Clock mockClock = mock(Clock.class);
    DummyJsonFileSource dummy =
        new DummyJsonFileSource("", mockClock, Duration.ofSeconds(10), new ObjectMapper());

    InputStream s = new ByteArrayInputStream(TEST_ITEM.getBytes(StandardCharsets.UTF_8));
    Item item = dummy.parse(s);
    assertEquals(1, item.getId());
  }
}
