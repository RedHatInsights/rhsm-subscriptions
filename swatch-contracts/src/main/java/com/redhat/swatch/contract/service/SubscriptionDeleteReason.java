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
package com.redhat.swatch.contract.service;

/** Why a subscription row was removed during sync reconcile or partner entitlement upsert. */
public enum SubscriptionDeleteReason {
  /** Subscription id exists in SWATCH but was absent from the latest upstream search response. */
  NOT_IN_UPSTREAM_RESPONSE,

  /** Offering SKU matches the configured product denylist. */
  PRODUCT_DENYLIST,

  /** Upstream DTO rejected because effective start date is null. */
  FILTERED_NULL_START_DATE,

  /** Upstream DTO rejected because start date is beyond SUBSCRIPTION_IGNORE_STARTING_LATER_THAN. */
  FILTERED_START_TOO_FAR_IN_FUTURE,

  /** Upstream DTO rejected because end date is before SUBSCRIPTION_IGNORE_EXPIRED_OLDER_THAN. */
  FILTERED_END_TOO_FAR_IN_PAST,

  /**
   * PAYG subscription segment removed during partner entitlement upsert because its start date is
   * not present in the IT Partner Gateway contract segments for the subscription number.
   */
  PARTNER_ENTITLEMENT_START_DATE_NOT_IN_GATEWAY,

  /** Subscription removed because its parent contract was deleted. */
  CONTRACT_DELETED
}
