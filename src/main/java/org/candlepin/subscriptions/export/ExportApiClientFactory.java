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

import com.redhat.swatch.clients.ClientConfig;
import com.redhat.swatch.clients.TokenProvider;
import com.redhat.swatch.clients.export.SwatchExportApi;
import java.util.Objects;
import org.springframework.beans.factory.FactoryBean;

public class ExportApiClientFactory implements FactoryBean<SwatchExportApi> {

  private final ClientConfig config;
  private final TokenProvider tokenProvider;

  public ExportApiClientFactory(ClientConfig config, TokenProvider tokenProvider) {
    this.config = Objects.requireNonNull(config, "config");
    this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
  }

  @Override
  public SwatchExportApi getObject() {
    return new SwatchExportApi(config, tokenProvider);
  }

  @Override
  public Class<?> getObjectType() {
    return SwatchExportApi.class;
  }
}
