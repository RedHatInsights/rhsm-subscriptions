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
package com.redhat.swatch.kessel;

import io.grpc.StatusRuntimeException;

/**
 * Exception wrapper for transient gRPC errors that should be retried.
 *
 * <p>Used to distinguish transient failures (UNAVAILABLE, DEADLINE_EXCEEDED, etc.) from
 * non-transient errors (PERMISSION_DENIED, NOT_FOUND, etc.). In manual retry loops, only this
 * exception type triggers retry.
 */
public class KesselTransientException extends RuntimeException {

  public KesselTransientException(StatusRuntimeException cause) {
    super("Transient Kessel failure: " + cause.getStatus().getCode(), cause);
  }

  @Override
  public synchronized StatusRuntimeException getCause() {
    return (StatusRuntimeException) super.getCause();
  }
}
