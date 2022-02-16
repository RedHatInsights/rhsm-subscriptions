package com.redhat.swatch.processors;

import javax.enterprise.context.ApplicationScoped;
import com.redhat.swatch.openapi.TallySummary;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class TallyTopicProcessor {

  private static final String INGRESS_CHANNEL = "ingress";

  @Incoming(INGRESS_CHANNEL)
  public void process(TallySummary tallySummary) {

    log.info("Picked up tally message {} to process", tallySummary);

  }
}
