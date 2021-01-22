/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.exception.mapper;

import static org.candlepin.subscriptions.security.SecurityConfig.*;

import org.candlepin.subscriptions.exception.ExceptionUtil;
import org.candlepin.subscriptions.utilization.api.model.Error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * The base class for all rhsm-subscription exception mappers. Its intent is to build the same
 * response no matter the exception.
 *
 * @param <T> the throwable that is to be mapped
 */
public abstract class BaseExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {
  private static final Logger log = LoggerFactory.getLogger(BaseExceptionMapper.class);

  @Override
  public Response toResponse(T exception) {
    Error error = buildError(exception);
    String message =
        StringUtils.hasText(error.getCode())
            ? String.format("%s: %s", error.getCode(), error.getTitle())
            : error.getTitle();
    Class<?> exClass = exception.getClass();
    if (AccessDeniedException.class.isAssignableFrom(exClass)
        || AuthenticationException.class.isAssignableFrom(exClass)) {
      log.error(SECURITY_STACKTRACE, message, exception);
    } else {
      log.error(message, exception);
    }
    return ExceptionUtil.toResponse(error);
  }

  /**
   * Builds an Error model object that will be returned as the body of an error response.
   *
   * @param exception the exception to be transformed.
   * @return an Error object containing the appropriate code and message as deduced from the passed
   *     exception.
   */
  protected abstract Error buildError(T exception);
}
