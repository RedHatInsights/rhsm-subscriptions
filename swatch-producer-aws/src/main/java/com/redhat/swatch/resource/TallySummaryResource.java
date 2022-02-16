package com.redhat.swatch.resource;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.redhat.swatch.openapi.SampleResponse;
import com.redhat.swatch.openapi.SampleResponseData;
import com.redhat.swatch.openapi.SampleResponseMeta;
import com.redhat.swatch.openapi.TallySummary;
import com.redhat.swatch.openapi.TallySummaryApi;
import com.redhat.swatch.processors.MakeMyLifeEasierProducer;
import lombok.extern.slf4j.Slf4j;

// @ReportingAccessRequired
@Slf4j
public class TallySummaryResource implements TallySummaryApi {


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
