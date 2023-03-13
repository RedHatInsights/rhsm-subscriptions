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

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContractsException extends RuntimeException {
  private ErrorCode code;
  private String detail;

  /**
   * Instantiate an exception using the error code's description as the message.
   *
   * @param code error code; its description will be used as the exception message
   */
  public ContractsException(ErrorCode code) {
    this(code, code.getDescription());
  }

  /**
   * Instantiate an exception using the error code and a custom message.
   *
   * @param code error code
   * @param message exception message
   */
  public ContractsException(ErrorCode code, String message) {
    this(code, message, null);
  }

  /**
   * Instantiate an exception using the error code, a custom message, and some additional detail.
   *
   * @param code error code
   * @param message exception message
   * @param detail extra detail
   */
  public ContractsException(ErrorCode code, String message, String detail) {
    super(message);
    this.code = code;
    this.detail = detail;
  }
}
