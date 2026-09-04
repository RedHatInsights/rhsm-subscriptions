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
package com.redhat.swatch.component.tests.api.hbi;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Facts reported in the {@code yupana} namespace, synthesized automatically by {@link HostBuilder}
 * whenever Satellite or QPC facts are present on a host. Not part of any HBI normalization logic;
 * these are only used to keep seeded facts realistic.
 */
@Getter
@Builder(builderClassName = "Builder")
public final class YupanaFacts {

  private static final String ORG_ID_FACT = "org_id";
  private static final String SOURCE_FACT = "source";
  private static final String ACCOUNT_FACT = "account";
  private static final String YUPANA_HOST_ID_FACT = "yupana_host_id";
  private static final String REPORT_SLICE_ID_FACT = "report_slice_id";
  private static final String REPORT_PLATFORM_ID_FACT = "report_platform_id";

  private final String orgId;
  private final String source;
  private final String account;
  private final String yupanaHostId;
  private final String reportSliceId;
  private final String reportPlatformId;

  /**
   * Convert to the raw key/value map HBI expects for the {@code yupana} facts namespace. Only
   * fields that were explicitly set are included.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> facts = new LinkedHashMap<>();
    putIfPresent(facts, ORG_ID_FACT, orgId);
    putIfPresent(facts, SOURCE_FACT, source);
    putIfPresent(facts, ACCOUNT_FACT, account);
    putIfPresent(facts, YUPANA_HOST_ID_FACT, yupanaHostId);
    putIfPresent(facts, REPORT_SLICE_ID_FACT, reportSliceId);
    putIfPresent(facts, REPORT_PLATFORM_ID_FACT, reportPlatformId);
    return facts;
  }

  private static void putIfPresent(Map<String, Object> facts, String key, Object value) {
    if (value != null) {
      facts.put(key, value);
    }
  }
}
