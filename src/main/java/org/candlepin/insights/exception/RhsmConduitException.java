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
package org.candlepin.insights.exception;

import org.candlepin.insights.api.model.Error;

import javax.ws.rs.core.Response.Status;

/**
 * Application's base exception class. Provides a means to create an
 * Error object that should be typically return as part of an error
 * response.
 */
public class RhsmConduitException extends RuntimeException {

    private Status status;
    private String detail;
    private ErrorCode code;

    public RhsmConduitException(ErrorCode code, Status status, String message, String detail) {
        this(code, status, message, detail, null);
    }

    public RhsmConduitException(ErrorCode code, Status status, String message, Throwable e) {
        this(code, status, message, e.getMessage(), e);
    }

    public RhsmConduitException(ErrorCode code, Status status, String message, String detail, Throwable e) {
        super(message, e);
        this.code = code;
        this.status = status;
        this.detail = detail;
    }

    public ErrorCode getCode() {
        return this.code;
    }

    public Status getStatus() {
        return this.status;
    }

    public Error error() {
        return new Error()
            .code(this.code.getCode())
            .status(String.valueOf(status.getStatusCode()))
            .title(this.getMessage())
            .detail(this.detail);
    }

}
