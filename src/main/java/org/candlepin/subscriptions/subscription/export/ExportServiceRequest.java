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
package org.candlepin.subscriptions.subscription.export;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ExportServiceRequest {
  private final ConsoleCloudEvent cloudEvent;
  private final ResourceRequest resourceRequest;

  public ExportServiceRequest(ConsoleCloudEvent cloudEvent) {
    this.cloudEvent = cloudEvent;
    this.resourceRequest =
        cloudEvent.getData(ResourceRequest.class).stream().findFirst().orElse(null);
  }

  public boolean hasRequest() {
    return resourceRequest != null;
  }

  public UUID getId() {
    return cloudEvent.getId();
  }

  public String getSource() {
    return cloudEvent.getSource();
  }

  public boolean isRequestFor(String name) {
    return Objects.equals(getApplication(), name)
        && Objects.equals(getRequest().getResource(), name);
  }

  public ResourceRequestClass getRequest() {
    return resourceRequest.getResourceRequest();
  }

  public String getApplication() {
    return getRequest().getApplication();
  }

  public UUID getExportRequestUUID() {
    return getRequest().getExportRequestUUID();
  }

  public String getXRhIdentity() {
    return getRequest().getXRhIdentity();
  }

  public String getOrgId() {
    return cloudEvent.getOrgId();
  }

  public Map<String, Object> getFilters() {
    return getRequest().getFilters();
  }

  public Format getFormat() {
    return getRequest().getFormat();
  }
}
