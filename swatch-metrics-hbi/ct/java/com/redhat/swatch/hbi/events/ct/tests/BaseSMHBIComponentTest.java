package com.redhat.swatch.hbi.events.ct.tests;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.api.UnleashService;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.SwatchMetricsHbiRestService;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("azure")
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

}
