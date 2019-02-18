/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.exception.mapper;

import org.candlepin.insights.api.model.Error;
import org.candlepin.insights.exception.ErrorCode;

import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

/**
 * Exception mapper for calls to a resource method that fail to pass validation.
 */
@Component
@Provider
public class ConstraintViolationExceptionMapper extends BaseExceptionMapper<ConstraintViolationException> {
    public static final String ERROR_TITLE = ErrorCode.VALIDATION_FAILED_ERROR.getDescription();

    @Override
    protected Error buildError(ConstraintViolationException exception) {
        return new Error()
            .code(ErrorCode.VALIDATION_FAILED_ERROR.getCode())
            .status(String.valueOf(Status.BAD_REQUEST.getStatusCode()))
            .title(ERROR_TITLE)
            .detail(exception.getMessage());
    }
}
