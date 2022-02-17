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

  @Inject
  MakeMyLifeEasierProducer makeMyLifeEasierProducer;

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
