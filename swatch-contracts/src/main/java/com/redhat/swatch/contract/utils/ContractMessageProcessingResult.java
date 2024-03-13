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
package com.redhat.swatch.contract.utils;

import static com.redhat.swatch.contract.service.ContractService.FAILURE_MESSAGE;
import static com.redhat.swatch.contract.service.ContractService.SUCCESS_MESSAGE;

import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.repository.ContractEntity;
import lombok.Getter;

public enum ContractMessageProcessingResult {
  INVALID_MESSAGE_UNPROCESSED("Bad message, see logs for details", false),
  RH_ORG_NOT_ASSOCIATED("Contract missing RH orgId", false),
  CONTRACT_DETAILS_MISSING("Empty value in non-null fields", false),
  PARTNER_API_FAILURE("An Error occurred while calling Partner Api", false),
  REDUNDANT_MESSAGE_IGNORED("Redundant message ignored", true),
  METADATA_UPDATED("Contract metadata updated", true),
  CONTRACT_SPLIT_DUE_TO_CAPACITY_UPDATE(
      "Previous contract archived and new contract created", true),
  NEW_CONTRACT_CREATED("New contract created", true);

  private final String message;
  private final String status;

  @Getter private final boolean valid;

  @Getter private ContractEntity entity;

  ContractMessageProcessingResult(String message, boolean valid) {
    this.message = message;
    this.valid = valid;
    this.status = valid ? SUCCESS_MESSAGE : FAILURE_MESSAGE;
  }

  public ContractMessageProcessingResult withContract(ContractEntity entity) {
    this.entity = entity;
    return this;
  }

  public StatusResponse toStatus() {
    return new StatusResponse().message(message).status(status);
  }
}
