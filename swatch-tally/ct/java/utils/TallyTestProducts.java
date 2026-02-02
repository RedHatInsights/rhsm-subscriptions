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
package utils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Test-only productTag + metricId definitions used by swatch-tally component tests. */
public enum TallyTestProducts {
  RHEL_FOR_X86("rhel-for-x86", "RHEL for x86", "Sockets", "SOCKETS"),
  RHEL_FOR_X86_ELS_PAYG("204", "rhel-for-x86-els-payg", "vCPUs", "VCPUS"),
  RHEL_FOR_X86_ELS_UNCONVERTED(
      "rhel-for-x86-els-unconverted", "rhel-for-x86-els-unconverted", "Sockets", "SOCKETS"),
  RHACM("rhacm", "rhacm", "vCPUs"),
  ROSA("rosa", "rosa", "Cores", "Instance-hours"),
  OPENSHIFT_DEDICATED(
      "openshift-dedicated-metrics", "OpenShift-dedicated-metrics", "Cores", "Instance-hours");

  private final String productId;
  private final String productTag;
  private final List<String> metricIds;

  TallyTestProducts(String productId, String productTag, String... metricIds) {
    this.productId = productId;
    this.productTag = productTag;
    this.metricIds = List.copyOf(Arrays.asList(metricIds));
  }

  public String productId() {
    return productId;
  }

  public String productTag() {
    return productTag;
  }

  public List<String> metricIds() {
    return metricIds;
  }

  public static List<TallyTestProducts> all() {
    return List.of(values());
  }

  public static Optional<TallyTestProducts> byProductTag(String productTag) {
    return all().stream().filter(p -> p.productTag.equalsIgnoreCase(productTag)).findFirst();
  }
}
