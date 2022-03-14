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
package org.candlepin.subscriptions.tally;

import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.cloudigrade.ApiException;
import org.candlepin.subscriptions.cloudigrade.CloudigradeService;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.model.UsageCount;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Collects the max values from accounts in cloudigrade. */
@Component
public class CloudigradeAccountUsageCollector {
  private static final Logger log = LoggerFactory.getLogger(CloudigradeAccountUsageCollector.class);

  private final CloudigradeService cloudigradeService;
  private final Map<String, Set<String>> archToProductMap;
  private final Map<String, Set<String>> roleToProductsMap;

  public CloudigradeAccountUsageCollector(
      CloudigradeService cloudigradeService, TagProfile tagProfile) {
    this.cloudigradeService = cloudigradeService;
    this.roleToProductsMap = tagProfile.getRoleToTagLookup();
    this.archToProductMap = tagProfile.getArchToTagLookup();
  }

  /**
   * Add measurements from cloudigrade to usage calculations
   *
   * @param accountCalcs map of existing account to usage calculations
   * @param accounts list of accounts to enrich calculations for
   * @throws IOException if role to product mappings or arch to product mappings can't be read
   * @throws ApiException if the cloudigrade service errs
   */
  @Timed("rhsm-subscriptions.snapshots.cloudigrade")
  public void enrichUsageWithCloudigradeData(
      Map<String, AccountUsageCalculation> accountCalcs, Collection<String> accounts)
      throws ApiException, org.candlepin.subscriptions.cloudigrade.internal.ApiException {

    for (String account : accounts) {
      log.trace("Cloudigrade checking for user {}", account);
      if (cloudigradeService.cloudigradeUserExists(account)) {
        log.trace("Cloudigrade found user {}", account);
        enrichUsageWithCloudigradeData(accountCalcs, account);
      }
    }
  }

  private void enrichUsageWithCloudigradeData(
      Map<String, AccountUsageCalculation> accountCalcs, String account) throws ApiException {
    log.trace("Fetching cloudigrade data for {}", account);
    ConcurrencyReport cloudigradeUsage =
        cloudigradeService.listDailyConcurrentUsages(account, null, null, null, null);
    accountCalcs.putIfAbsent(account, new AccountUsageCalculation(account));
    AccountUsageCalculation usageCalc = accountCalcs.get(account);
    if (cloudigradeUsage.getData().size() > 1) {
      log.warn("Got more than one day's worth of data from cloudigrade; using the first");
    }
    cloudigradeUsage.getData().stream()
        .findFirst()
        .ifPresent(
            usage -> {
              for (UsageCount usageCount : usage.getMaximumCounts()) {
                try {
                  // null service-type may occur if integrating with an older version of cloudigrade
                  if (!"_ANY".equals(usageCount.getServiceType())
                      && usageCount.getServiceType() != null) {
                    continue; // skip service-type for now, we don't yet support it
                  }
                  UsageCalculation.Key key =
                      extractKey(usageCount, archToProductMap, roleToProductsMap);
                  UsageCalculation calculation = usageCalc.getOrCreateCalculation(key);
                  Integer count = usageCount.getInstancesCount();
                  calculation.addCloudigrade(HardwareMeasurementType.AWS_CLOUDIGRADE, count);
                } catch (IllegalArgumentException e) {
                  log.warn("Skipping cloudigrade usage due to: {}", e.toString());
                  log.debug("Usage record: {}", usageCount);
                }
              }
            });
  }

  private UsageCalculation.Key extractKey(
      UsageCount usageCount,
      Map<String, Set<String>> archToProductMap,
      Map<String, Set<String>> roleToProductsMap) {
    String productId = extractProductId(usageCount, archToProductMap, roleToProductsMap);
    ServiceLevel sla = ServiceLevel.fromString(usageCount.getSla());
    Usage usage = Usage.fromString(usageCount.getUsage());
    // FIXME cloudigrade does report usage yet, workaround below
    if (usageCount.getUsage() == null) {
      usage = Usage._ANY;
    }
    return new UsageCalculation.Key(productId, sla, usage);
  }

  private String extractProductId(
      UsageCount usageCount,
      Map<String, Set<String>> archToProductMap,
      Map<String, Set<String>> roleToProductsMap) {
    String role = usageCount.getRole();
    String arch = usageCount.getArch();
    if ("_ANY".equals(role) && "_ANY".equals(arch)) {
      return "RHEL";
    } else if ("_ANY".equals(arch)) {
      Optional<String> mapped =
          roleToProductsMap.getOrDefault(role, Collections.emptySet()).stream()
              .filter(p -> !p.equals("RHEL"))
              .findFirst();

      if (!mapped.isPresent()) {
        throw new IllegalArgumentException("No mapping for role: " + role);
      }
      return mapped.get();
    } else if ("_ANY".equals(role)) {
      Optional<String> mapped =
          archToProductMap.getOrDefault(arch, Collections.emptySet()).stream().findFirst();

      if (!mapped.isPresent()) {
        throw new IllegalArgumentException("No mapping for arch: " + arch);
      }
      return mapped.get();
    } else {
      throw new IllegalArgumentException(
          String.format("Combination of role: %s and arch: %s invalid", role, arch));
    }
  }
}
