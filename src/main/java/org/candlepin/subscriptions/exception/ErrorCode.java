/*
 * Copyright (c) 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.exception;

/**
 * Represents the various application codes.
 *
 * SUBSCRIPTIONS1XXX: General application error code space.
 * SUBSCRIPTIONS2XXX: Insights Inventory API related application error code space.
 *
 */
public enum ErrorCode {

    /**
     * An unhandled exception has occurred. This typically implies that the
     * exception was unexpected and likely due to a bug or coding error.
     */
    UNHANDLED_EXCEPTION_ERROR(1000, "An unhandled exception occurred"),

    /**
     * An exception was thrown by RestEasy while processing a request to the
     * rhsm-conduit API. This typically means that an HTTP client error has
     * occurred (HTTP 4XX).
     */
    REQUEST_PROCESSING_ERROR(1001, "An error occurred while processing a request."),

    /**
     * The client's request is malformed in some way and does not pass validation.
     */
    VALIDATION_FAILED_ERROR(1002, "Client request failed validation."),

    /**
     * The client's request was denied due to lack of roles/permissions.
     */
    REQUEST_DENIED_ERROR(1003, "Request was denied due to lack of roles/permissions."),

    /**
     * The client's request was denied because opt-in has not yet occurred.
     */
    OPT_IN_REQUIRED(1004, "Request was denied since opt-in has not yet occurred."),

    /**
     * An unexpected exception was thrown by the inventory service client.
     */
    INVENTORY_SERVICE_ERROR(2000, "Inventory Service Error"),

    /**
     * The inventory service is unavailable. This typically means that the
     * inventory service is either down, or the configured service URL is
     * not correct, and a connection can not be made.
     */
    INVENTORY_SERVICE_UNAVAILABLE(2001, "The inventory service is unavailable"),

    /**
     * An exception was thrown by the inventory service when a request was made.
     * This typically means that an HTTP client error has occurred (HTTP 4XX)
     * when the request was made.
     */
    INVENTORY_SERVICE_REQUEST_ERROR(2002, "Inventory API Error"),

    // Metering Errors
    SUBSCRIPTION_SERVICE_REQUEST_ERROR(3000, "Subscription Service Error"),

    SUBSCRIPTION_SERVICE_MARKETPLACE_ID_LOOKUP_ERROR(3001, "Could not find marketplace subscription id");

    private final String CODE_PREFIX = "SUBSCRIPTIONS";

    private String code;
    private String description;

    ErrorCode(int intCode, String description) {
        this.code = CODE_PREFIX + intCode;
        this.description = description;
    }

    public String getCode() {
        return this.code;
    }

    public String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", code, description);
    }

}
