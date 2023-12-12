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
package com.redhat.swatch.azure.file;

import java.util.ArrayList;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AzureMarketplaceCredentials {
  private Credentials credentials;

  public ArrayList<Client> getClients() {
    return Optional.ofNullable(credentials.getAzure()).map(Azure::getClients)
        .orElse(new ArrayList<>());
  }

  @NoArgsConstructor
  @Getter
  public static class Credentials {
    private Azure azure;
  }

  @NoArgsConstructor
  @Getter
  public static class Azure {

    private ArrayList<Client> clients;
  }

  @NoArgsConstructor
  @Getter
  public static class Client {

    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String publisher;
  }
}
