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
package com.redhat.swatch.aws.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
  AWS_UNPROCESSED_RECORDS_ERROR(1000, "Some AWS UsageRecords were not processed"),
  AWS_DIMENSION_NOT_CONFIGURED(1001, "Aws Dimension not configured"),
  AWS_REQUEST_ERROR(1002, "AWS request failed"),
  AWS_MISSING_CREDENTIALS_ERROR(1003, "AWS credentials missing"),
  AWS_USAGE_CONTEXT_LOOKUP_ERROR(1004, "Error looking up AWS usage context"),
  AWS_MANUAL_SUBMISSION_DISABLED(1005, "Manual submission disabled."),
  SUBSCRIPTION_RECENTLY_TERMINATED(1006, "Subscription recently terminated"),
  USAGE_TIMESTAMP_OUT_OF_RANGE(1007, "Usage timestamp will not be accepted by AWS");

  private static final String CODE_PREFIX = "SWATCHAWS";

  private final String code;
  private final String description;

  ErrorCode(int intCode, String description) {
    this.code = CODE_PREFIX + intCode;
    this.description = description;
  }

  @Override
  public String toString() {
    return String.format("%s: %s", code, description);
  }
}
