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
package com.redhat.swatch.azure.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
  AZURE_UNPROCESSED_RECORDS_ERROR(1000, "Some Azure UsageEvents were not processed"),
  AZURE_MANUAL_SUBMISSION_DISABLED(1005, "Manual submission disabled."),
  SUBSCRIPTION_RECENTLY_TERMINATED(1006, "Subscription recently terminated"),
  USAGE_TIMESTAMP_OUT_OF_RANGE(1007, "Usage timestamp will not be accepted by AWS"),
  AZURE_MARKETPLACE_API_REQUEST_FAILED(1008, "Azure Marketplace Api request failed."),
  AZURE_USAGE_CONTEXT_LOOKUP_ERROR(1009, "Error looking up Azure Usage Context."),
  SUBSCRIPTION_CANNOT_BE_DETERMINED(
      1010,
      "Multiple possible matches found. Likely a subscription is missing part of its billingAccountId.");

  private static final String CODE_PREFIX = "AZURE";

  ErrorCode(int intCode, String description) {
    this.code = CODE_PREFIX + intCode;
    this.description = description;
  }

  private final String code;
  private final String description;
}
