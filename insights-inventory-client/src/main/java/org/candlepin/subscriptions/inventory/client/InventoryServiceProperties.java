/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.inventory.client;

import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/** Sub-class for inventory service properties */
public class InventoryServiceProperties {
  private boolean useStub;
  private String url;
  private String apiKey;
  private String kafkaHostIngressTopic = "platform.inventory.host-ingress";
  private int apiHostUpdateBatchSize = 50;
  private int staleHostOffsetInDays = 0;
  private boolean addUuidHyphens = false;

  @DurationUnit(ChronoUnit.HOURS)
  private Duration hostLastSyncThreshold = Duration.ofHours(24);

  public boolean isUseStub() {
    return useStub;
  }

  public void setUseStub(boolean useStub) {
    this.useStub = useStub;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public int getApiHostUpdateBatchSize() {
    return apiHostUpdateBatchSize;
  }

  public void setApiHostUpdateBatchSize(int apiHostUpdateBatchSize) {
    this.apiHostUpdateBatchSize = apiHostUpdateBatchSize;
  }

  public int getStaleHostOffsetInDays() {
    return staleHostOffsetInDays;
  }

  public void setStaleHostOffsetInDays(int staleHostOffsetInDays) {
    this.staleHostOffsetInDays = staleHostOffsetInDays;
  }

  public String getKafkaHostIngressTopic() {
    return kafkaHostIngressTopic;
  }

  public void setKafkaHostIngressTopic(String kafkaHostIngressTopic) {
    this.kafkaHostIngressTopic = kafkaHostIngressTopic;
  }

  public Duration getHostLastSyncThreshold() {
    return hostLastSyncThreshold;
  }

  public void setHostLastSyncThreshold(Duration hostLastSyncThreshold) {
    this.hostLastSyncThreshold = hostLastSyncThreshold;
  }

  public boolean isAddUuidHyphens() {
    return addUuidHyphens;
  }

  public void setAddUuidHyphens(boolean addUuidHyphens) {
    this.addUuidHyphens = addUuidHyphens;
  }
}
