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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClowderJsonTest {
  /**
   * An abbreviated and augmented example of the clowder JSON we'll see. I deleted a lot of entries
   * and also added some data types that were not normally present.
   */
  public static final String TEST_JSON =
      "{\n"
          + "    \"database\": {\n"
          + "        \"adminUsername\": \"postgres\",\n"
          + "        \"hostname\": \"rhsm-clowdapp-db.rhsm.svc\",\n"
          + "        \"name\": \"rhsm-db\",\n"
          + "        \"port\": 5432,\n"
          + "        \"sslMode\": \"disable\"\n"
          + "    },\n"
          + "    \"endpoints\": [\n"
          + "        {\n"
          + "            \"app\": \"rhsm-clowdapp\",\n"
          + "            \"hostname\": \"rhsm-clowdapp-service.rhsm.svc\",\n"
          + "            \"name\": \"service\",\n"
          + "            \"port\": 8000\n"
          + "        },\n"
          + "        {\n"
          + "            \"app\": \"rbac\",\n"
          + "            \"hostname\": \"rbac-service.rhsm.svc\",\n"
          + "            \"name\": \"service\",\n"
          + "            \"port\": 8000\n"
          + "        }\n"
          + "    ],\n"
          + "    \"metricsPath\": \"/metrics\",\n"
          + "    \"metricsPort\": 9000,\n"
          + "    \"someBigNumber\": 9999999999,\n"
          + "    \"useAwesomeMode\": true,\n"
          + "    \"fractionOfAwesomeMode\": 0.75,\n"
          + "    \"aStringAsAnInt\": \"123\",\n"
          + "    \"aBinaryBlob\": \"SGVsbG8gV29ybGQ=\"\n"
          + "}\n";

  public static final String EMPTY_JSON = "{}";

  public static final String TEST_ITEM =
      "{\n" + "    \"id\": 1,\n" + "    \"itemName\": \"theItem\"\n" + "}";

  public static class Item {
    private int id;
    private String itemName;

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

  ClowderJson init(String json) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    return new ClowderJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), mapper);
  }

  @Test
  void getStringTest() throws IOException {
    var clowderJson = init(TEST_JSON);
    assertEquals("postgres", clowderJson.getString(toPointer("/database/adminUsername")));
  }

  @Test
  void getBooleanTest() throws IOException {
    var clowderJson = init(TEST_JSON);
    assertEquals(true, clowderJson.getBoolean(toPointer("/useAwesomeMode")));
  }

  @Test
  void getIntegerTest() throws IOException {
    var clowderJson = init(TEST_JSON);
    assertEquals(9000, clowderJson.getInteger(toPointer("/metricsPort")));
  }

  @Test
  void getLongTest() throws IOException {
    var clowderJson = init(TEST_JSON);
    assertEquals(9999999999L, clowderJson.getLong(toPointer("/someBigNumber")));
  }

  @Test
  void getDoubleTest() throws IOException {
    var clowderJson = init(TEST_JSON);
    assertEquals(0.75, clowderJson.getDouble(toPointer("/fractionOfAwesomeMode")));
  }

  @Test
  void getBytesTest() throws IOException {
    var clowderJson = init(TEST_JSON);
    assertArrayEquals(
        "Hello World".getBytes(StandardCharsets.UTF_8),
        clowderJson.getBytes(toPointer("/aBinaryBlob")));
  }

  @Test
  void getNestedValueTest() throws IOException {
    var clowderJson = init(TEST_JSON);
    assertEquals("rbac", clowderJson.getString(toPointer("/endpoints/1/app")));
  }

  private JsonPointer toPointer(String s) {
    return JsonPointer.compile(s);
  }

  @Test
  void emptyJsonStringTest() throws IOException {
    var clowderJson = init(EMPTY_JSON);
    assertThrows(
        IllegalStateException.class, () -> clowderJson.getString(toPointer("/doesNotExist")));
    assertThrows(
        IllegalStateException.class, () -> clowderJson.getBoolean(toPointer("/doesNotExist")));
    assertThrows(
        IllegalStateException.class, () -> clowderJson.getInteger(toPointer("/doesNotExist")));
    assertThrows(
        IllegalStateException.class, () -> clowderJson.getLong(toPointer("/doesNotExist")));
    assertThrows(
        IllegalStateException.class, () -> clowderJson.getDouble(toPointer("/doesNotExist")));
    assertThrows(
        IllegalStateException.class, () -> clowderJson.getBytes(toPointer("/doesNotExist")));
  }

  @Test
  void getTypeTest() throws IOException {
    var clowderJson = init(TEST_ITEM);
    Item actual = clowderJson.getType(Item.class);
    assertEquals("theItem", actual.getItemName());
  }
}
