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
package com.redhat.swatch.contract.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.model.ContractSyncTask;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractSyncTaskConsumerTest {

  @Mock private ContractService contractService;
  private ContractSyncTaskConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new ContractSyncTaskConsumer(contractService);
  }

  @Test
  void shouldInvokeSyncContractsForOrgId() {
    var statusResponse = org.mockito.Mockito.mock(StatusResponse.class);
    when(statusResponse.getStatus()).thenReturn(ContractService.SUCCESS_MESSAGE);
    when(contractService.syncContractsByOrgId("org123")).thenReturn(statusResponse);

    consumer.consumeFromTopic(new ContractSyncTask("org123"));

    verify(contractService).syncContractsByOrgId("org123");
    verify(statusResponse).getStatus();
    verifyNoMoreInteractions(statusResponse);
    verifyNoMoreInteractions(contractService);
  }

  @Test
  void shouldNotThrowWhenSyncReturnsFailedStatus() {
    var statusResponse = org.mockito.Mockito.mock(StatusResponse.class);
    when(statusResponse.getStatus()).thenReturn(ContractService.FAILURE_MESSAGE);
    when(statusResponse.getMessage()).thenReturn("upstream error");
    when(contractService.syncContractsByOrgId("org456")).thenReturn(statusResponse);

    Assertions.assertDoesNotThrow(() -> consumer.consumeFromTopic(new ContractSyncTask("org456")));

    verify(contractService).syncContractsByOrgId("org456");
    verify(statusResponse).getStatus();
    verify(statusResponse).getMessage();
    verifyNoMoreInteractions(statusResponse);
    verifyNoMoreInteractions(contractService);
  }

  @Test
  void shouldContinueConsumingAfterException() {
    when(contractService.syncContractsByOrgId("org789")).thenThrow(new RuntimeException("boom"));
    when(contractService.syncContractsByOrgId("org790"))
        .thenReturn(new StatusResponse().status(ContractService.SUCCESS_MESSAGE));

    Assertions.assertDoesNotThrow(() -> consumer.consumeFromTopic(new ContractSyncTask("org789")));
    Assertions.assertDoesNotThrow(() -> consumer.consumeFromTopic(new ContractSyncTask("org790")));

    verify(contractService).syncContractsByOrgId("org789");
    verify(contractService).syncContractsByOrgId("org790");
    verifyNoMoreInteractions(contractService);
  }
}
