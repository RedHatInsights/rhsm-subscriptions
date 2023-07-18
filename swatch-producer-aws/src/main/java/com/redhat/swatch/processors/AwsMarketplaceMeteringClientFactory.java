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
package com.redhat.swatch.processors;

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AwsUsageContext;
import com.redhat.swatch.files.AwsCredentialsLookup;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClient;
import software.amazon.awssdk.services.marketplacemetering.MarketplaceMeteringClientBuilder;

@ApplicationScoped
public class AwsMarketplaceMeteringClientFactory {
  private final AwsCredentialsLookup awsCredentialsLookup;
  private final boolean awsMarketplaceEndpointOverride;
  private final String awsMarketplaceEndpointUrl;
  private final String awsRegion;

  public AwsMarketplaceMeteringClientFactory(
      AwsCredentialsLookup awsCredentialsLookup,
      @ConfigProperty(name = "AWS_MARKETPLACE_ENDPOINT_OVERRIDE")
          boolean awsMarketplaceEndpointOverride,
      @ConfigProperty(name = "AWS_MARKETPLACE_ENDPOINT_URL") String awsMarketplaceEndpointUrl,
      @ConfigProperty(name = "AWS_REGION") String awsRegion) {
    this.awsMarketplaceEndpointOverride = awsMarketplaceEndpointOverride;
    this.awsMarketplaceEndpointUrl = awsMarketplaceEndpointUrl;
    this.awsRegion = awsRegion;
    this.awsCredentialsLookup = awsCredentialsLookup;
  }

  public MarketplaceMeteringClient buildMarketplaceMeteringClient(AwsUsageContext context) {
    MarketplaceMeteringClientBuilder builder = MarketplaceMeteringClient.builder();
    if (awsMarketplaceEndpointOverride) {
      builder = builder.endpointOverride(URI.create(awsMarketplaceEndpointUrl));
    }
    if (awsRegion != null) {
      builder = builder.region(Region.of(awsRegion));
    }
    return builder
        .credentialsProvider(
            awsCredentialsLookup.getCredentialsProvider(context.getAwsSellerAccountId()))
        .build();
  }
}
