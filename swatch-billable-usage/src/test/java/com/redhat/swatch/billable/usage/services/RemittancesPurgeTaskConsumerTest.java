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
package com.redhat.swatch.billable.usage.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.configuration.ApplicationConfiguration;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.model.EnabledOrgsResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemittancesPurgeTaskConsumerTest {
  @Mock ApplicationConfiguration configuration;
  @Mock BillableUsageRemittanceRepository remittanceRepository;
  @Mock ApplicationClock clock;
  @InjectMocks RemittancesPurgeTaskConsumer consumer;

  @Test
  void testWhenConsumeWithoutPolicyThenNothingHappens() {
    when(configuration.getRemittanceRetentionPolicyDuration()).thenReturn(null);
    consumer.consume(new EnabledOrgsResponse());
    verifyNoInteractions(remittanceRepository);
  }

  @Test
  void testWhenConsumeWithPolicyThenPurgeHappens() {
    String expectedOrgId = "org123";
    when(clock.now()).thenReturn(OffsetDateTime.now());
    when(configuration.getRemittanceRetentionPolicyDuration()).thenReturn(Duration.ofMinutes(5));
    consumer.consume(new EnabledOrgsResponse().withOrgId(expectedOrgId));
    verify(remittanceRepository)
        .deleteAllByOrgIdAndRemittancePendingDateBefore(eq(expectedOrgId), any());
  }
}
