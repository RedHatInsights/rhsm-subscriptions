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
package com.redhat.swatch.component.tests.api;

public class UnleashService extends RestService {

  private static final String EMIT_EVENTS = "swatch.swatch-metrics-hbi.emit-events";
  private static final String ADMIN_TOKEN =
      System.getenv("UNLEASH_ADMIN_TOKEN") != null
          ? System.getenv("UNLEASH_ADMIN_TOKEN")
          : "*:*.unleash-insecure-admin-api-token";

  @Override
  public void start() {
    super.start();
    enableFlag();
    disableFlag();
  }

  public void enableFlag() {
    given()
        .header("Authorization", ADMIN_TOKEN)
        .contentType("application/json")
        .when()
        .post(
            "/api/admin/projects/default/features/" + EMIT_EVENTS + "/environments/development/on")
        .then()
        .statusCode(200);
  }

  public void disableFlag() {
    given()
        .header("Authorization", ADMIN_TOKEN)
        .contentType("application/json")
        .when()
        .post(
            "/api/admin/projects/default/features/" + EMIT_EVENTS + "/environments/development/off")
        .then()
        .statusCode(200);
  }

  public boolean isFlagEnabled() {
    return given()
        .header("Authorization", ADMIN_TOKEN)
        .when()
        .get("/api/admin/projects/default/features/" + EMIT_EVENTS)
        .then()
        .statusCode(200)
        .extract()
        .path("environments.find { it.name == 'development' }.enabled");
  }
}
