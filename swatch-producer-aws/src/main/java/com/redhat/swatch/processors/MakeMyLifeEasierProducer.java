package com.redhat.swatch.processors;

import javax.enterprise.context.ApplicationScoped;
import com.redhat.swatch.openapi.TallySummary;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import lombok.extern.slf4j.Slf4j;



// TODO not sure if we want to keep this or not yet.  If we want to be able to submit
// to the endpoint in an async manner and queue the request, this will do that.
// If we want the endpoint to send to AWS and provide instant feedback, we don't want this.
// I kind of like the idea of queueing it up though because then it serves as a record of everything
// we've intended to sent to AWS
@Slf4j
@ApplicationScoped
public class MakeMyLifeEasierProducer {

  private static final String EGRESS_CHANNEL = "egress";

  @Channel(EGRESS_CHANNEL)
  Emitter<TallySummary> tallySummarySubmissionFarm;

  public void queueTallySummary(TallySummary tallySummary) {

    tallySummarySubmissionFarm.send(tallySummary);

    log.info("Queued up a TallySummary object to the tally topic");

  }

}
