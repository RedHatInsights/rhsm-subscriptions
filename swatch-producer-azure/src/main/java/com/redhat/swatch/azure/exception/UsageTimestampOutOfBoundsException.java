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

import static com.redhat.swatch.azure.exception.ErrorCode.USAGE_TIMESTAMP_OUT_OF_RANGE;

/**
 * When thrown, indicates that the usage being processed is outside the AWS APIs acceptable
 * timestamp range.
 *
 * <p>(See:
 * https://docs.aws.amazon.com/marketplacemetering/latest/APIReference/API_UsageRecord.html)
 */
public class UsageTimestampOutOfBoundsException extends AzureProducerException {

  public UsageTimestampOutOfBoundsException(String message) {
    super(USAGE_TIMESTAMP_OUT_OF_RANGE, message);
  }
}
