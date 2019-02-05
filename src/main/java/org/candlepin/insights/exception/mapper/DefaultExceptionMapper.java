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

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * The default exception mapper used to catch any Throwable that isn't already
 * being mapped by another mapper. This mapper will always produce a 500 HTTP
 * response containing the message from the exception.
 */
@Component
@Provider
public class DefaultExceptionMapper extends BaseExceptionMapper<Throwable> {

    @Override
    protected Error buildError(Throwable exception) {
        return new Error()
            .code(ErrorCode.UNHANDLED_EXCEPTION_ERROR.getCode())
            .status(String.valueOf(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
            .title("An internal server error has occurred. Check the server logs for further details.")
            .detail(exception.getMessage());
    }

}
