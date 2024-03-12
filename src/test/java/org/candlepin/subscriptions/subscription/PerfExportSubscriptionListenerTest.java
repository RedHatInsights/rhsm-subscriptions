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
package org.candlepin.subscriptions.subscription;

import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.test.ExtendWithExportServiceWireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(value = {"worker", "test", "capacity-ingress"})
class PerfExportSubscriptionListenerTest extends ExtendWithExportServiceWireMock {

  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final String APPLICATION_NAME = "SWATCH";
  private static final UUID RESOURCE_ID = UUID.randomUUID();

  @Autowired ExportSubscriptionListener listener;
  @Autowired SubscriptionRepository repository;
  @Autowired OfferingRepository offeringRepository;

  private ResourceRequestClass request;

  @BeforeEach
  void setup() {
    request = new ResourceRequestClass();
    request.setUUID(EXPORT_ID);
    request.setApplication(APPLICATION_NAME);
    request.setExportRequestUUID(RESOURCE_ID);

    stubExportUploadFor(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);

    for (int i = 0; i < 50000; i++) {
      repository.save(createSubscription());
    }
  }

  /** This is interesting to view the body in the console. */
  @Test
  @Transactional
  void verifyExportUploadWithSingleSubscription() {
    logExportRequestsBody(true);
    Stream<Subscription> data = repository.streamAll(Pageable.ofSize(1));
    listener.uploadJson(data, request);
    verifyExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
  }

  @Transactional
  @ParameterizedTest
  @ValueSource(ints = {1, 100, 1000, 10000, 50000})
  void verifyExportUpload(int size) {
    logExportRequestsBody(false);
    Stream<Subscription> data = repository.streamAll(Pageable.ofSize(size));
    listener.uploadJson(data, request);
    verifyExportUpload(EXPORT_ID, APPLICATION_NAME, RESOURCE_ID);
  }

  private Subscription createSubscription() {
    Subscription subscription = new Subscription();
    subscription.setBillingProviderId(UUID.randomUUID().toString());
    subscription.setSubscriptionId("1");
    subscription.setOrgId("2");
    subscription.setQuantity(4L);
    subscription.setStartDate(OffsetDateTime.now());
    subscription.setEndDate(OffsetDateTime.now());
    subscription.setSubscriptionNumber(UUID.randomUUID().toString());
    subscription.setBillingProvider(BillingProvider.RED_HAT);
    subscription.setBillingAccountId(UUID.randomUUID().toString());
    Offering offering = new Offering();
    offering.setSku(UUID.randomUUID().toString());
    subscription.setOffering(offering);

    offeringRepository.save(offering);

    return subscription;
  }
}
