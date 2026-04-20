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

import com.redhat.swatch.utilization.data.OrgUtilizationPreferenceEntity;
import com.redhat.swatch.utilization.data.OrgUtilizationPreferenceRepository;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class OrgPreferencesService {

  private final OrgUtilizationPreferenceRepository repository;

  /**
   * Persists {@link OrgPreferencesRequest#getCustomThreshold()} for the organization and returns
   * the stored value in the response body.
   */
  @Transactional
  public OrgPreferencesResponse updateOrgPreferences(String orgId, OrgPreferencesRequest request) {
    log.info("Updating utilization preference orgId={}", orgId);
    var entity = getOrCreateOrgPreferenceEntity(orgId);
    entity.setCustomThreshold(request.getCustomThreshold());
    repository.persist(entity);
    var response = new OrgPreferencesResponse();
    response.setCustomThreshold(request.getCustomThreshold());
    log.debug("Updated utilization preference '{}' to orgId={}", request, orgId);
    return response;
  }

  private OrgUtilizationPreferenceEntity getOrCreateOrgPreferenceEntity(String orgId) {
    return repository
        .findByIdOptional(orgId)
        .orElseGet(
            () -> {
              var entity = new OrgUtilizationPreferenceEntity();
              entity.setOrgId(orgId);
              return entity;
            });
  }
}
