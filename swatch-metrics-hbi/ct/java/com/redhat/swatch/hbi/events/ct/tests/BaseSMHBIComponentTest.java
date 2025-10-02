package com.redhat.swatch.hbi.events.ct.tests;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.api.UnleashService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.SwatchMetricsHbiRestService;
import com.redhat.swatch.hbi.model.FlushResponse;
import java.time.Duration;
import lombok.Getter;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("metrics-hbi")
public class BaseSMHBIComponentTest {
  
  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService()
          .subscribeToTopic(Topics.HBI_EVENT_IN)
          .subscribeToTopic(Topics.SWATCH_SERVICE_INSTANCE_INGRESS);

  @Unleash
  static UnleashService unleash = new UnleashService();

  @Quarkus(service = "swatch-metrics-hbi")
  static SwatchMetricsHbiRestService swatchMetricsHbi = new SwatchMetricsHbiRestService();

  /**
   * Flush the outbox and continue until the expected flush count is reached, or
   * we reach the configured await timeout.
   *
   * @param expectedFlushCount the expected number of records to flush.
   */
  protected void flushOutbox(int expectedFlushCount) {
    Counter counter = new Counter();
    AwaitilitySettings settings = AwaitilitySettings.usingTimeout(Duration.ofSeconds(10))
        .timeoutMessage("Unable to flush the expected number of outbox records in time: %s",
            expectedFlushCount);

    AwaitilityUtils.untilIsTrue(() -> {
      while (counter.getCount() < expectedFlushCount) {
        FlushResponse response = swatchMetricsHbi.flushOutboxSynchronously();
        if (response.getStatus() == FlushResponse.StatusEnum.SUCCESS &&
            response.getCount() > 0) {
          counter.increment(response.getCount());
          if (counter.getCount() < expectedFlushCount) {
            return false;
          }
        }
      }
      return true;
    }, settings);
  }

  @Getter
  private class Counter {
    private long count = 0;

    public void increment(long amount) {
      count += amount;
    }

    public void increment() {
      count++;
    }

    public long getCount() {
      return count;
    }
  }
}
