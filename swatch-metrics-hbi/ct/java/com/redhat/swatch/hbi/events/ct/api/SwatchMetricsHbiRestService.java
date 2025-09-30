package com.redhat.swatch.hbi.events.ct.api;

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.hbi.model.FlushResponse;
import java.util.Map;

public class SwatchMetricsHbiRestService extends SwatchService {
  private static final String API_ROOT = "/api/swatch-metrics-hbi";

  public FlushResponse flushOutboxSynchronously() {
    return given()
        .log().all()
        .headers(
            Map.of(
                "Content-Type", "application/json",
                "x-rh-swatch-synchronous-request", "true",
                "x-rh-swatch-psk", "placeholder"
            ))
        .put(String.format("%s/internal/rpc/outbox/flush", API_ROOT))
        .then()
        .statusCode(200)
        .extract()
        .body()
        .as(FlushResponse.class);
  }

}
