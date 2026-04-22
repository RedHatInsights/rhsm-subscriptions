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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.model.ContractSyncTask;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractSyncTaskConsumerTest {

  @Mock private ContractService contractService;
  private MeterRegistry meterRegistry;
  private ContractSyncTaskConsumer consumer;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    consumer = new ContractSyncTaskConsumer(contractService, meterRegistry);
  }

  @Test
  void testConsumeCallsSyncContractsByOrgId() {
    when(contractService.syncContractsByOrgId("org123"))
        .thenReturn(new StatusResponse().status(ContractService.SUCCESS_MESSAGE));

    consumer.consumeFromTopic(new ContractSyncTask("org123"));

    verify(contractService).syncContractsByOrgId("org123");
    assertEquals(
        1.0, meterRegistry.counter("swatch_contract_sync_task", "outcome", "success").count());
    assertEquals(
        0.0, meterRegistry.counter("swatch_contract_sync_task", "outcome", "failure").count());
  }

  @Test
  void testConsumeIncrementsFailureOnFailedStatus() {
    when(contractService.syncContractsByOrgId("org456"))
        .thenReturn(
            new StatusResponse().status(ContractService.FAILURE_MESSAGE).message("upstream error"));

    consumer.consumeFromTopic(new ContractSyncTask("org456"));

    verify(contractService).syncContractsByOrgId("org456");
    assertEquals(
        0.0, meterRegistry.counter("swatch_contract_sync_task", "outcome", "success").count());
    assertEquals(
        1.0, meterRegistry.counter("swatch_contract_sync_task", "outcome", "failure").count());
  }

  @Test
  void testConsumeIncrementsFailureOnException() {
    when(contractService.syncContractsByOrgId("org789")).thenThrow(new RuntimeException("boom"));

    consumer.consumeFromTopic(new ContractSyncTask("org789"));

    verify(contractService).syncContractsByOrgId("org789");
    assertEquals(
        0.0, meterRegistry.counter("swatch_contract_sync_task", "outcome", "success").count());
    assertEquals(
        1.0, meterRegistry.counter("swatch_contract_sync_task", "outcome", "failure").count());
  }
}
