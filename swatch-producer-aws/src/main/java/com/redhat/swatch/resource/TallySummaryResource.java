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
package com.redhat.swatch.resource;

import com.redhat.swatch.openapi.model.SampleResponse;
import com.redhat.swatch.openapi.model.SampleResponseData;
import com.redhat.swatch.openapi.model.SampleResponseMeta;
import com.redhat.swatch.openapi.model.TallySummary;
import com.redhat.swatch.openapi.resource.TallySummaryApi;
import com.redhat.swatch.processors.MakeMyLifeEasierProducer;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @ReportingAccessRequired
public class TallySummaryResource implements TallySummaryApi {

  private static final Logger log = LoggerFactory.getLogger(TallySummaryResource.class);

  @Inject MakeMyLifeEasierProducer makeMyLifeEasierProducer;

  @Override
  public SampleResponse submitTallySummary(@Valid @NotNull TallySummary tallySummary) {

    log.info("{}", tallySummary);

    makeMyLifeEasierProducer.queueTallySummary(tallySummary);

    var meta = new SampleResponseMeta();
    meta.accountNumber(tallySummary.getAccountNumber());

    var data = new SampleResponseData();
    data.setSubmissionSuccessful(true);

    var response = new SampleResponse();
    response.setMeta(meta);
    response.setData(data);
    return response;
  }
}
