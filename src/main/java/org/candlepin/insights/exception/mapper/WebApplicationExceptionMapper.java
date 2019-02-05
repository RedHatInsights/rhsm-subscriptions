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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

/**
 * An exception mapper used to override the default exception mapping done by
 * resteasy. This implementation generates and returns an Errors object as the
 * response Json instead of redirecting to an error page.
 */
@Component
@Provider
public class WebApplicationExceptionMapper extends BaseExceptionMapper<WebApplicationException> {

    @Override
    protected Error buildError(WebApplicationException wae) {
        return new Error()
            .code(ErrorCode.REQUEST_PROCESSING_ERROR.getCode())
            .status(String.valueOf(wae.getResponse().getStatus()))
            .title("An rhsm-conduit API error has occurred.")
            .detail(wae.getMessage());
    }

}
