/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.conduit.inventory.kafka;

/**
 * Represents the kafka message that should be sent to the inventory service. Each message
 * should include an operation, a metadata object and a data object that will be converted
 * to JSON when sent.
 *
 * The inventory service's message consumer is expecting a JSON message in the following format:
 * <pre>
 *    {
 *      "operation": $SUPPORTED_OPERATION,
 *      "metadata": $M_AS_JSON_STRING,
 *      "data": $D_AS_JSON_STRING
 *    }
 * </pre>
 *
 * @param <M> the type of the metadata object to be included in the JSON message.
 * @param <D> the type of the data object to be included in the JSON message.
 */
public abstract class HostOperationMessage<M, D> {

    protected String operation;
    protected M metadata;
    protected D data;

    protected HostOperationMessage() {
    }

    protected HostOperationMessage(String operation, M metadata, D data) {
        this.operation = operation;
        this.metadata = metadata;
        this.data = data;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public M getMetadata() {
        return metadata;
    }

    public void setMetadata(M metadata) {
        this.metadata = metadata;
    }

    public D getData() {
        return data;
    }

    public void setData(D data) {
        this.data = data;
    }
}
