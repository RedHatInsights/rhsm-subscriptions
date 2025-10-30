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
package com.redhat.swatch.hbi.events.exception.api;

import lombok.Getter;

@Getter
public enum ErrorCode {
  UNHANDLED_EXCEPTION(1000, "An unhandled exception occurred"),
  INTERNAL_SERVICE_ERROR(1001, "An internal service error occurred"),
  SYNCHRONOUS_OUTBOX_FLUSH_ERROR(1002, "An error while flushing the outbox synchronously"),
  SYNCHRONOUS_OUTBOX_FLUSH_DISABLED(1003, "The synchronous outbox flushing is disabled"),
  EXISTING_OUTBOX_FLUSH_ERROR(
      1004, "Outbox flush was not performed because another flush is already in progress"),
  ;

  private static final String CODE_PREFIX = "SMHBI";

  private final String code;
  private final String description;

  ErrorCode(int intCode, String description) {
    this.code = CODE_PREFIX + intCode;
    this.description = description;
  }
}
