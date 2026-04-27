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
package com.redhat.swatch.utilization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.utilization.data.OrgUtilizationPreferenceEntity;
import com.redhat.swatch.utilization.data.OrgUtilizationPreferenceRepository;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
class OrgPreferencesServiceTest {

  private static final String ORG_ID = "org-456";

  @InjectMock OrgUtilizationPreferenceRepository repository;

  @Inject OrgPreferencesService service;

  @Test
  void getOrgPreferences_whenPreferenceExists_returnsPreference() {
    var entity = new OrgUtilizationPreferenceEntity();
    entity.setOrgId(ORG_ID);
    entity.setCustomThreshold(7);
    when(repository.findByIdOptional(ORG_ID)).thenReturn(Optional.of(entity));

    var response = service.getOrgPreferences(ORG_ID);

    assertEquals(7, response.getCustomThreshold());
  }

  @Test
  void getOrgPreferences_whenPreferenceDoesNotExist_returnsDefaultThreshold() {
    when(repository.findByIdOptional(ORG_ID)).thenReturn(Optional.empty());

    var response = service.getOrgPreferences(ORG_ID);

    assertEquals(80, response.getCustomThreshold());
  }

  @Test
  void whenNoExistingPreference_persistsNewEntity() {
    when(repository.findByIdOptional(ORG_ID)).thenReturn(Optional.empty());
    var request = new OrgPreferencesRequest();
    request.setCustomThreshold(11);

    var response = service.updateOrgPreferences(ORG_ID, request);

    assertEquals(11, response.getCustomThreshold());
    var captor = ArgumentCaptor.forClass(OrgUtilizationPreferenceEntity.class);
    verify(repository).persist(captor.capture());
    assertEquals(ORG_ID, captor.getValue().getOrgId());
    assertEquals(11, captor.getValue().getCustomThreshold());
  }

  @Test
  void whenPreferenceExists_updatesThresholdWithoutPersist() {
    var existing = new OrgUtilizationPreferenceEntity();
    existing.setOrgId(ORG_ID);
    existing.setCustomThreshold(3);
    when(repository.findByIdOptional(ORG_ID)).thenReturn(Optional.of(existing));
    var request = new OrgPreferencesRequest();
    request.setCustomThreshold(15);

    var response = service.updateOrgPreferences(ORG_ID, request);

    assertEquals(15, response.getCustomThreshold());
    assertEquals(15, existing.getCustomThreshold());
  }
}
