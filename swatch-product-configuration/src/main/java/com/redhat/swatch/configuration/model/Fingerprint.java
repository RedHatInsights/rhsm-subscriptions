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
package com.redhat.swatch.configuration.model;

import java.util.List;
import lombok.*;

/**
 * Technical fingerprint is an identifiable capabilities provided by software packages. "A typical
 * sysadmin can easily distinguish between technical fingerprints (e.g. RHEL for Power vs. RHEL for
 * x86) - e.g. cat /etc/redhat-release" Typical identifiers include: (1) Engineering IDs - product
 * content IDs used to identify use of the subscription. (2) Architectures: used to identify a
 * specific architecture w/ a given subscription.
 */
@Data
@Builder
public class Fingerprint {
  private List<String> engineeringIds;
  private List<String> arches;
}
