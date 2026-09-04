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
package com.redhat.swatch.component.tests.api.hbi;

import java.util.UUID;
import java.util.function.Function;

/**
 * Host-level templates that shape a host across multiple reporters/system-profile facts at once.
 *
 * <p>Apply via {@link HostBuilder#apply}, a single explicit action - templates aren't meant to be
 * composed with each other:
 *
 * <pre>
 * hostManager.createHost(orgId)
 *     .apply(HostTemplates.physicalRhel(2, 4))
 *     .displayName("Custom")
 *     .insert();
 * </pre>
 */
public final class HostTemplates {

  private HostTemplates() {}

  /** Physical RHEL host with the given socket/core counts. */
  public static Function<HostBuilder, HostBuilder> conduitReportedPhysicalRhel(
      int sockets, int cores) {
    return builder ->
        builder
            .rhsmFacts(RhsmFacts.builder().defaultFacts().build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("physical")
                    .arch("x86_64")
                    .numberOfSockets(sockets)
                    .numberOfCpus(cores)
                    .build());
  }

  /** AWS RHEL host with the given socket/core counts. */
  public static Function<HostBuilder, HostBuilder> conduitReportedAwsVirtualRhel(
      int sockets, int cores) {
    return builder ->
        builder
            .rhsmFacts(RhsmFacts.builder().defaultFacts().isVirtual(true).build())
            .systemProfileFacts(
                SystemProfileFacts.builder()
                    .infrastructureType("virtual")
                    .cloudProvider("aws")
                    .arch("x86_64")
                    .numberOfSockets(sockets)
                    .numberOfCpus(cores)
                    .build())
            .providerId("i-test-" + UUID.randomUUID().toString().substring(0, 12));
  }
}
