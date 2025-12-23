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
package domain;

import com.redhat.swatch.contract.test.model.TallySnapshot;

public enum BillingProvider {
  AWS,
  AZURE;

  public String toApiModel() {
    return switch (this) {
      case AWS -> "aws";
      case AZURE -> "azure";
      default -> throw new IllegalArgumentException("Unsupported billing provider: " + this);
    };
  }

  public TallySnapshot.BillingProvider toTallySnapshotModel() {
    return switch (this) {
      case AWS -> TallySnapshot.BillingProvider.AWS;
      case AZURE -> TallySnapshot.BillingProvider.AZURE;
      default -> throw new IllegalArgumentException("Unsupported billing provider: " + this);
    };
  }
}
