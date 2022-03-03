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
package com.redhat.swatch.processors;

import com.redhat.swatch.openapi.model.TallySummary;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO not sure if we want to keep this or not yet.  If we want to be able to submit
// to the endpoint in an async manner and queue the request, this will do that.
// If we want the endpoint to send to AWS and provide instant feedback, we don't want this.
// I kind of like the idea of queueing it up though because then it serves as a record of everything
// we've intended to sent to AWS
@ApplicationScoped
public class MakeMyLifeEasierProducer {
  private static final Logger log = LoggerFactory.getLogger(MakeMyLifeEasierProducer.class);

  private static final String EGRESS_CHANNEL = "egress";

  @Channel(EGRESS_CHANNEL)
  Emitter<TallySummary> tallySummarySubmissionFarm;

  public void queueTallySummary(TallySummary tallySummary) {

    tallySummarySubmissionFarm.send(tallySummary);

    log.info("Queued up a TallySummary object to the tally topic");
  }
}
