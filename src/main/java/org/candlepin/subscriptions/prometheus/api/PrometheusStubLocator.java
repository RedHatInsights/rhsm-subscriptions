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

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import org.webjars.NotFoundException;

/** Utility class to locate the prometheus json file used for stubs. */
public final class PrometheusStubLocator {

  public static final URL STUB =
      PrometheusStubLocator.class.getResource("/prometheus-stub-data/success.json");

  private PrometheusStubLocator() {}

  public static File getStubFile() {
    try {
      return new File(STUB.toURI());
    } catch (URISyntaxException e) {
      throw new NotFoundException(
          "The '/prometheus-stub-data/success.json' file could not be loaded");
    }
  }
}
