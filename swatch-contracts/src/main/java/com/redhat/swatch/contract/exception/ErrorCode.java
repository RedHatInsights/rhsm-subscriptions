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
package com.redhat.swatch.contract.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
  CONTRACT_EXISTS(
      1001, "There's already an active contract for that productId & subscriptionNumber"),
  CONTRACT_DOES_NOT_EXIST(1002, "Contract does not exist"),
  SUBSCRIPTION_RECENTLY_TERMINATED(
      1005, "Subscription recently terminated. No active subscriptions."),
  SUBSCRIPTION_SERVICE_REQUEST_ERROR(3000, "Subscription Service Error"),
  OFFERING_MISSING_ERROR(3003, "Sku not present in Offering"),
  BAD_UPDATE(4000, "Bad update request"),
  UNHANDLED_EXCEPTION(5000, "Unhandled exception"),
  VALIDATION_FAILED_ERROR(1002, "Client request failed validation."),
  /**
   * An exception was thrown by RestEasy while processing a request to the rhsm-conduit API. This
   * typically means that an HTTP client error has occurred (HTTP 4XX).
   */
  REQUEST_PROCESSING_ERROR(5001, "An error occurred while processing a request.");

  private static final String CODE_PREFIX = "CONTRACTS";

  ErrorCode(int intCode, String description) {
    this.code = CODE_PREFIX + intCode;
    this.description = description;
  }

  private String code;
  private String description;
}
