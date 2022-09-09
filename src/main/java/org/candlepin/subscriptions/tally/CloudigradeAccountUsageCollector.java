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
import java.util.*;
import org.candlepin.subscriptions.cloudigrade.ApiException;
import org.candlepin.subscriptions.cloudigrade.CloudigradeService;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.model.UsageCount;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

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
   * @param account account to enrich calculations for
   * @param orgId org id to enrich calculations
   * @throws ApiException if the cloudigrade service errs
   */
  @Timed("rhsm-subscriptions.snapshots.cloudigrade")
  public void enrichUsageWithCloudigradeData(
      Map<String, AccountUsageCalculation> accountCalcs, String account, String orgId)
      throws ApiException, org.candlepin.subscriptions.cloudigrade.internal.ApiException {
    log.info("Cloudigrade enriching usage using org {} and account {}", orgId, account);
    ConcurrencyReport cloudigradeUsage = getDailyConcurrencyReport(orgId);
    if (cloudigradeUsage == null) {
      return;
    }

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

  @Nullable
  protected ConcurrencyReport getDailyConcurrencyReport(String orgId)
      throws org.candlepin.subscriptions.cloudigrade.internal.ApiException, ApiException {
    ConcurrencyReport cloudigradeUsage = null;

    if (ObjectUtils.isEmpty(orgId)) {
      /* As of this writing (9 Sep 2022), Cloudigrade does still support the
      X-RH-CLOUDIGRADE-ACCOUNT-NUMBER header, so this statement is not true in the narrow sense.
      However, sending both the account number header and the X-RH-CLOUDIGRADE-ORG-ID is a
      recipe for confusion if one of them is empty (See SWATCH-545).  OpenAPI does not make it
      easy to declare mutually exclusive parameters, so I've elected to just ban account number
      since we are transitioning away from it anyway.
      */
      log.warn("Cloudigrade requires an org ID to authenticate");
      return null;
    }

    log.debug("Cloudigrade checking user for org {}", orgId);
    if (cloudigradeService.cloudigradeUserExists(orgId)) {
      log.debug("Fetching cloudigrade data for org {}", orgId);
      cloudigradeUsage =
          cloudigradeService.listDailyConcurrentUsages(orgId, null, null, null, null);
    }
    log.debug("Cloudigrade could not find user for org {}", orgId);

    return cloudigradeUsage;
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

    return new UsageCalculation.Key(productId, sla, usage, BillingProvider._ANY, "_ANY");
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

      if (mapped.isEmpty()) {
        throw new IllegalArgumentException("No mapping for role: " + role);
      }
      return mapped.get();
    } else if ("_ANY".equals(role)) {
      Optional<String> mapped =
          archToProductMap.getOrDefault(arch, Collections.emptySet()).stream().findFirst();

      if (mapped.isEmpty()) {
        throw new IllegalArgumentException("No mapping for arch: " + arch);
      }
      return mapped.get();
    } else {
      throw new IllegalArgumentException(
          String.format("Combination of role: %s and arch: %s invalid", role, arch));
    }
  }
}
